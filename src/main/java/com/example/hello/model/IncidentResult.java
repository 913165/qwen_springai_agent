package com.example.hello.model;

/**
 * Result of the createIncident tool / skip decision (Agent 2).
 */
public record IncidentResult(
		boolean incidentCreated,
		String incidentId,
		String status,
		String category,
		String severity,
		String reason
) {
}
