package com.example.hello.tool;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.hello.model.IncidentResult;

/**
 * Agent 2 tool — create a mock incident ticket.
 */
@Component
public class CreateIncidentTool {

	private final AtomicInteger sequence = new AtomicInteger(0);
	private volatile IncidentResult lastResult;

	@Tool(name = "createIncident", description = """
			Create a mock incident ticket for a UPI failure.
			Call ONLY when classification says incidentRequired=true.
			Do NOT call for simple customer errors like insufficient funds or invalid PIN.
			""")
	public String createIncident(
			@ToolParam(description = "Runtime transaction ID") String transactionId,
			@ToolParam(description = "Failure code") String failureCode,
			@ToolParam(description = "Incident category") String category,
			@ToolParam(description = "Severity") String severity,
			@ToolParam(description = "Short root cause") String rootCause) {

		String incidentId = "INC-2026-%04d".formatted(sequence.incrementAndGet());

		lastResult = new IncidentResult(
				true,
				incidentId,
				"OPEN",
				category,
				severity,
				"Incident created for " + failureCode + " / " + rootCause);

		System.out.println("[TOOL] createIncident CALLED");
		System.out.println("       id=" + incidentId
				+ ", txn=" + transactionId
				+ ", category=" + category
				+ ", severity=" + severity);

		return "{\"incidentId\":\"" + incidentId
				+ "\",\"status\":\"OPEN\",\"category\":\"" + category
				+ "\",\"severity\":\"" + severity + "\"}";
	}

	public IncidentResult getLastResult() {
		return lastResult;
	}

	public void clear() {
		lastResult = null;
	}
}
