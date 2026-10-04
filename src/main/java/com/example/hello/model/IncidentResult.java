package com.example.hello.model;

/**
 * Structured output from Agent 2 — Incident Creation / skip decision.
 */
public record IncidentResult(
		boolean incidentCreated,
		String incidentId,
		String status,
		String category,
		String severity,
		String transactionId,
		String failureCode,
		String failureType,
		String rootCause,
		String incidentInformation,
		String investigationSteps,
		String escalationTeam,
		String reason
) {
}
