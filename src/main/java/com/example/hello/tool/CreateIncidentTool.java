package com.example.hello.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Mock createIncident tool for the Agentic RAG demo.
 * Does not connect to any real incident-management system.
 */
@Component
public class CreateIncidentTool {

	private static final Logger logger = LoggerFactory.getLogger(CreateIncidentTool.class);

	private final AtomicInteger sequence = new AtomicInteger(0);
	private final ConcurrentMap<String, Map<String, String>> incidents = new ConcurrentHashMap<>();

	@Tool(name = "createIncident", description = """
			Create a mock operational incident for a UPI transaction failure.
			Call ONLY when Classification Agent set incidentRequired=true.
			Do NOT call for customer errors such as insufficient funds or invalid PIN.
			""")
	public String createIncident(
			@ToolParam(description = "Runtime transaction ID") String transactionId,
			@ToolParam(description = "Failure code from the transaction, e.g. U30") String failureCode,
			@ToolParam(description = "Failure type from classification") String failureType,
			@ToolParam(description = "Severity: LOW, MEDIUM, HIGH, or CRITICAL") String severity,
			@ToolParam(description = "Root cause from classification") String rootCause,
			@ToolParam(description = "Incident category, e.g. BANK_SERVICE_DEGRADATION") String incidentCategory,
			@ToolParam(description = "Operator-facing incident information") String incidentInformation,
			@ToolParam(description = "Investigation steps for operators") String investigationSteps,
			@ToolParam(description = "Escalation team") String escalationTeam) {

		String incidentId = "INC-2026-%04d".formatted(sequence.incrementAndGet());

		Map<String, String> record = new LinkedHashMap<>();
		record.put("incidentId", incidentId);
		record.put("status", "OPEN");
		record.put("category", nullToEmpty(incidentCategory));
		record.put("severity", nullToEmpty(severity));
		record.put("transactionId", nullToEmpty(transactionId));
		record.put("failureCode", nullToEmpty(failureCode));
		record.put("failureType", nullToEmpty(failureType));
		record.put("rootCause", nullToEmpty(rootCause));
		record.put("incidentInformation", nullToEmpty(incidentInformation));
		record.put("investigationSteps", nullToEmpty(investigationSteps));
		record.put("escalationTeam", nullToEmpty(escalationTeam));
		incidents.put(incidentId, record);

		logger.info("Mock incident created: {} for txn={} failureCode={} category={}",
				incidentId, transactionId, failureCode, incidentCategory);

		return """
				{"incidentId":"%s","status":"OPEN","category":"%s","severity":"%s","transactionId":"%s","failureCode":"%s","failureType":"%s","rootCause":"%s","incidentInformation":"%s","investigationSteps":"%s","escalationTeam":"%s"}
				""".formatted(
						incidentId,
						escape(incidentCategory),
						escape(severity),
						escape(transactionId),
						escape(failureCode),
						escape(failureType),
						escape(rootCause),
						escape(incidentInformation),
						escape(investigationSteps),
						escape(escalationTeam))
				.trim();
	}

	public Map<String, Map<String, String>> getIncidents() {
		return Map.copyOf(incidents);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String escape(String value) {
		return nullToEmpty(value).replace("\"", "'").replace("\n", " ");
	}
}
