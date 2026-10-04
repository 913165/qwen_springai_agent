package com.example.hello.model;

/**
 * Structured output from Agent 1 — Failure Classification.
 * Grounded in RAG generic failure knowledge + the runtime transaction.
 */
public record ClassificationResult(
		String transactionId,
		String failureType,
		String failureCode,
		String severity,
		String rootCause,
		String symptoms,
		String recommendedAction,
		boolean retryAllowed,
		boolean incidentRequired,
		String incidentCategory,
		String incidentInformation,
		String investigationSteps,
		String escalationTeam
) {
}
