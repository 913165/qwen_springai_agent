package com.example.hello.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.hello.model.ClassificationResult;
import com.example.hello.model.IncidentResult;
import com.example.hello.model.TransactionFailureRequest;
import com.example.hello.model.WorkflowResponse;
import com.example.hello.tool.CreateIncidentTool;

/**
 * Sequential Chain Workflow (Spring AI agentic pattern):
 *
 * New Transaction → RAG Retrieval → Classification Agent → Incident Agent → Final Response
 *
 * Runtime transactions are NOT stored in the vector store.
 * RAG holds generic UPI failure knowledge only.
 */
@Service
public class UpiFailureWorkflow {

	private static final String CLASSIFICATION_SYSTEM = """
			You are Agent 1 — UPI Failure Classification Agent.

			You receive:
			1) A NEW runtime transaction/failure (facts about this specific payment)
			2) Retrieved RAG documents with GENERIC UPI failure knowledge

			Rules:
			- Use RAG knowledge to interpret the failure code/message (failure type, root cause,
			  recommended action, severity, incident requirements, investigation steps, escalation).
			- Combine that knowledge with the actual runtime transaction fields.
			- transactionId MUST come from the runtime transaction, never from RAG.
			- Do NOT invent incident guidance when relevant RAG knowledge is available.
			- Prefer matching on failureCode / symptoms from RAG.

			Return structured output with:
			transactionId, failureType, failureCode, severity, rootCause, symptoms,
			recommendedAction, retryAllowed, incidentRequired, incidentCategory,
			incidentInformation, investigationSteps, escalationTeam.

			incidentRequired guidance:
			- false for customer errors (INSUFFICIENT_FUNDS, INVALID_PIN, daily/customer limits, invalid VPA)
			- true for bank timeouts/outages (e.g. U30), NPCI issues, fraud/risk needing review, technical failures
			""";

	private static final String INCIDENT_SYSTEM = """
			You are Agent 2 — UPI Incident Agent.

			You receive the COMPLETE structured ClassificationResult from Agent 1.
			Execute sequentially based only on that result.

			If incidentRequired=true:
			- MUST call createIncident with transactionId, failureCode, failureType, severity,
			  rootCause, incidentCategory, incidentInformation, investigationSteps, escalationTeam.
			- Return incidentCreated=true using the tool result (incidentId, status OPEN, category, severity).

			If incidentRequired=false:
			- Do NOT call createIncident.
			- Return incidentCreated=false, incidentId=null, status=SKIPPED.
			- Explain in reason why no incident is required and include recommendedAction for the customer/ops.
			""";

	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	private final CreateIncidentTool createIncidentTool;

	public UpiFailureWorkflow(ChatClient.Builder chatClientBuilder,
			VectorStore vectorStore,
			CreateIncidentTool createIncidentTool) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
		this.createIncidentTool = createIncidentTool;
	}

	public WorkflowResponse process(TransactionFailureRequest transaction) {
		validate(transaction);

		// 1) RAG retrieval — query by failure semantics, NOT by transactionId
		String ragQuery = buildRagQuery(transaction);
		List<Document> docs = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(ragQuery)
						.topK(3)
						.build());

		List<String> retrievedKnowledge = docs.stream()
				.map(Document::getText)
				.toList();

		String knowledgeBlock = retrievedKnowledge.isEmpty()
				? "(no matching generic failure knowledge found)"
				: String.join("\n---\n", retrievedKnowledge);

		// 2) Agent 1 — Classification (structured)
		ClassificationResult classification = chatClient.prompt()
				.system(CLASSIFICATION_SYSTEM)
				.user("""
						Runtime transaction (NOT in RAG knowledge base):
						transactionId=%s
						bank=%s
						psp=%s
						transactionType=%s
						amount=%s
						status=%s
						failureCode=%s
						errorMessage=%s

						Retrieved generic RAG knowledge:
						%s
						""".formatted(
						safe(transaction.transactionId()),
						safe(transaction.bank()),
						safe(transaction.psp()),
						safe(transaction.transactionType()),
						transaction.amount() == null ? "(not provided)" : transaction.amount(),
						safe(transaction.status()),
						safe(transaction.failureCode()),
						safe(transaction.errorMessage()),
						knowledgeBlock))
				.call()
				.entity(ClassificationResult.class);

		// 3) Agent 2 — Incident decision / createIncident tool (structured)
		IncidentResult incident = chatClient.prompt()
				.system(INCIDENT_SYSTEM)
				.user("""
						Complete Classification Agent output:
						transactionId=%s
						failureType=%s
						failureCode=%s
						severity=%s
						rootCause=%s
						symptoms=%s
						recommendedAction=%s
						retryAllowed=%s
						incidentRequired=%s
						incidentCategory=%s
						incidentInformation=%s
						investigationSteps=%s
						escalationTeam=%s
						""".formatted(
						classification.transactionId(),
						classification.failureType(),
						classification.failureCode(),
						classification.severity(),
						classification.rootCause(),
						classification.symptoms(),
						classification.recommendedAction(),
						classification.retryAllowed(),
						classification.incidentRequired(),
						classification.incidentCategory(),
						classification.incidentInformation(),
						classification.investigationSteps(),
						classification.escalationTeam()))
				.tools(createIncidentTool)
				.call()
				.entity(IncidentResult.class);

		// 4) Final response
		return new WorkflowResponse(transaction, classification, incident, retrievedKnowledge);
	}

	/**
	 * Build RAG query from failure semantics only — never use transactionId as knowledge key.
	 */
	private static String buildRagQuery(TransactionFailureRequest tx) {
		return Stream.of(tx.failureCode(), tx.errorMessage(), tx.status(), tx.bank())
				.filter(StringUtils::hasText)
				.map(String::trim)
				.collect(Collectors.joining(" "));
	}

	private static void validate(TransactionFailureRequest tx) {
		if (tx == null) {
			throw new IllegalArgumentException("Transaction body is required");
		}
		if (!StringUtils.hasText(tx.failureCode()) && !StringUtils.hasText(tx.errorMessage())) {
			throw new IllegalArgumentException("Provide failureCode and/or errorMessage");
		}
	}

	private static String safe(String value) {
		return StringUtils.hasText(value) ? value.trim() : "(not provided)";
	}
}
