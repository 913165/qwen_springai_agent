package com.example.hello.model;

/**
 * Runtime UPI transaction/failure submitted at request time.
 * Not stored in the RAG knowledge base.
 */
public record TransactionFailureRequest(
		String transactionId,
		String bank,
		String psp,
		String transactionType,
		Double amount,
		String status,
		String failureCode,
		String errorMessage
) {
}
