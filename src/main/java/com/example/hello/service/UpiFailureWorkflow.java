package com.example.hello.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.hello.model.ClassificationResult;
import com.example.hello.model.ClassificationRule;
import com.example.hello.model.IncidentResult;
import com.example.hello.model.TransactionFailureRequest;
import com.example.hello.model.WorkflowResponse;
import com.example.hello.tool.ClassifyFailureTool;
import com.example.hello.tool.CreateIncidentTool;

/**
 * Simple flow:
 * 1) RAG search (top 3) — knowledge only
 * 2) Classify using classify.json + LLM/tool help
 * 3) If no classification → OTHER + createIncident
 * 4) If incidentRequired → createIncident tool
 */
@Service
public class UpiFailureWorkflow {

	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	private final ClassifyCatalogService classifyCatalog;
	private final ClassifyFailureTool classifyFailureTool;
	private final CreateIncidentTool createIncidentTool;

	public UpiFailureWorkflow(ChatClient.Builder chatClientBuilder,
			VectorStore vectorStore,
			ClassifyCatalogService classifyCatalog,
			ClassifyFailureTool classifyFailureTool,
			CreateIncidentTool createIncidentTool) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
		this.classifyCatalog = classifyCatalog;
		this.classifyFailureTool = classifyFailureTool;
		this.createIncidentTool = createIncidentTool;
	}

	public WorkflowResponse process(TransactionFailureRequest tx) {
		validate(tx);
		classifyFailureTool.clear();
		createIncidentTool.clear();

		// 1) RAG SEARCH ONLY
		String ragQuery = Stream.of(tx.failureCode(), tx.errorMessage())
				.filter(StringUtils::hasText)
				.map(String::trim)
				.collect(Collectors.joining(" "));

		System.out.println("[1/4] RAG search topK=3, query=" + ragQuery);
		List<Document> docs = vectorStore.similaritySearch(
				SearchRequest.builder().query(ragQuery).topK(3).build());
		List<String> top3 = docs.stream().map(Document::getText).toList();
		for (int i = 0; i < docs.size(); i++) {
			Document d = docs.get(i);
			System.out.println("      match[" + (i + 1) + "] score="
					+ (d.getScore() != null ? d.getScore() : 0.0));
		}
		String ragBlock = top3.isEmpty() ? "(no RAG matches)" : String.join("\n---\n", top3);

		// 2) Resolve candidate from classify.json using RAG + failureCode
		Optional<ClassificationRule> matchedRule = classifyCatalog.resolveFromRagAndCode(tx.failureCode(), top3);
		boolean noMatch = matchedRule.isEmpty();
		ClassificationRule ruleForPrompt = matchedRule.orElse(classifyCatalog.getFallback());

		System.out.println("[2/4] classify.json lookup => "
				+ (noMatch ? "NO MATCH → will use OTHER fallback" : "MATCH " + ruleForPrompt.failureCode()));

		// 3) Agent 1: LLM + classifyFailure tool, guided by classify.json
		System.out.println("[3/4] Agent 1 classification (classify.json + LLM tool)...");
		chatClient.prompt()
				.system("""
						You are Classification Agent.
						Search/RAG only provides supporting knowledge.
						You MUST classify using classify.json rules.

						Rules:
						1) Prefer the provided matched classify.json entry if present.
						2) If no match, classify as OTHER from fallback.
						3) Call classifyFailure tool exactly once with values from classify.json.
						""")
				.user("""
						Runtime transaction:
						transactionId=%s
						bank=%s
						failureCode=%s
						errorMessage=%s

						Top 3 RAG matches (knowledge only):
						%s

						Matched classify.json entry (or OTHER fallback if no match):
						failureCode=%s
						failureType=%s
						severity=%s
						rootCause=%s
						recommendedAction=%s
						incidentRequired=%s
						incidentCategory=%s
						catalogMatchFound=%s

						Call classifyFailure now using this classify.json entry.
						""".formatted(
						tx.transactionId(),
						tx.bank(),
						tx.failureCode(),
						tx.errorMessage(),
						ragBlock,
						ruleForPrompt.failureCode(),
						ruleForPrompt.failureType(),
						ruleForPrompt.severity(),
						ruleForPrompt.rootCause(),
						ruleForPrompt.recommendedAction(),
						ruleForPrompt.incidentRequired(),
						ruleForPrompt.incidentCategory(),
						!noMatch))
				.tools(classifyFailureTool)
				.options(OpenAiChatOptions.builder()
						.temperature(0.0)
						.maxTokens(350)
						.extraBody(Map.of("enable_thinking", false)))
				.call()
				.content();

		ClassificationResult classification = classifyFailureTool.getLastResult();
		if (classification == null) {
			System.out.println("[3/4] LLM did not call classifyFailure — applying classify.json entry directly");
			classification = fromRule(tx.transactionId(), ruleForPrompt);
			classifyFailureTool.classifyFailure(
					classification.transactionId(),
					classification.failureCode(),
					classification.failureType(),
					classification.severity(),
					classification.rootCause(),
					classification.incidentRequired(),
					classification.incidentCategory(),
					classification.recommendedAction());
			classification = classifyFailureTool.getLastResult();
		}

		// If still no useful classification / forced OTHER path
		if (noMatch || "OTHER".equalsIgnoreCase(classification.failureCode())
				|| "UNCLASSIFIED".equalsIgnoreCase(classification.failureType())) {
			System.out.println("[3/4] No catalog classification → OTHER + create incident ticket");
			ClassificationRule other = classifyCatalog.getFallback();
			classification = fromRule(tx.transactionId(), other);
			// ensure tool state mirrors OTHER
			classifyFailureTool.clear();
			classifyFailureTool.classifyFailure(
					classification.transactionId(),
					classification.failureCode(),
					classification.failureType(),
					classification.severity(),
					classification.rootCause(),
					true,
					classification.incidentCategory(),
					classification.recommendedAction());
			classification = classifyFailureTool.getLastResult();
		}

		System.out.println("[3/4] Classification = " + classification.failureCode()
				+ " / " + classification.failureType()
				+ " incidentRequired=" + classification.incidentRequired());

		// 4) Agent 2: createIncident when required (always for OTHER)
		IncidentResult incident = runIncidentAgent(classification);

		System.out.println("[DONE] class=" + classification.failureCode()
				+ ", incidentCreated=" + incident.incidentCreated()
				+ ", incidentId=" + incident.incidentId());

		return new WorkflowResponse(tx, classification, incident, top3);
	}

	private IncidentResult runIncidentAgent(ClassificationResult classification) {
		if (!classification.incidentRequired()) {
			System.out.println("[4/4] incidentRequired=false → skip createIncident");
			return new IncidentResult(false, null, "SKIPPED", null, classification.severity(),
					"No incident. Action: " + classification.recommendedAction());
		}

		System.out.println("[4/4] Agent 2 createIncident tool...");
		chatClient.prompt()
				.system("""
						You are Incident Agent.
						Classification requires an incident.
						Call createIncident exactly once.
						""")
				.user("""
						transactionId=%s
						failureCode=%s
						category=%s
						severity=%s
						rootCause=%s
						""".formatted(
						classification.transactionId(),
						classification.failureCode(),
						classification.incidentCategory(),
						classification.severity(),
						classification.rootCause()))
				.tools(createIncidentTool)
				.options(OpenAiChatOptions.builder()
						.temperature(0.0)
						.maxTokens(250)
						.extraBody(Map.of("enable_thinking", false)))
				.call()
				.content();

		IncidentResult incident = createIncidentTool.getLastResult();
		if (incident == null) {
			System.out.println("[4/4] LLM skipped tool — creating incident directly");
			createIncidentTool.createIncident(
					classification.transactionId(),
					classification.failureCode(),
					classification.incidentCategory(),
					classification.severity(),
					classification.rootCause());
			incident = createIncidentTool.getLastResult();
		}
		return incident;
	}

	private static ClassificationResult fromRule(String transactionId, ClassificationRule rule) {
		return new ClassificationResult(
				transactionId,
				rule.failureCode(),
				rule.failureType(),
				rule.severity(),
				rule.rootCause(),
				rule.incidentRequired(),
				rule.incidentCategory(),
				rule.recommendedAction());
	}

	private static void validate(TransactionFailureRequest tx) {
		if (tx == null || (!StringUtils.hasText(tx.failureCode()) && !StringUtils.hasText(tx.errorMessage()))) {
			throw new IllegalArgumentException("Provide failureCode and/or errorMessage");
		}
	}
}
