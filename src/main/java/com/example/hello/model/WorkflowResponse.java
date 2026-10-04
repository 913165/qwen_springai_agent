package com.example.hello.model;

import java.util.List;

/**
 * Final response of the sequential two-agent UPI failure workflow.
 */
public record WorkflowResponse(
		TransactionFailureRequest transaction,
		ClassificationResult classification,
		IncidentResult incident,
		List<String> retrievedKnowledge
) {
}
