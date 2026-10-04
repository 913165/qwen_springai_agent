package com.example.hello.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.model.TransactionFailureRequest;
import com.example.hello.model.WorkflowResponse;
import com.example.hello.service.UpiFailureWorkflow;

/**
 * API for the sequential two-agent UPI failure workflow.
 * Existing /chat and /search endpoints remain unchanged.
 */
@RestController
@RequestMapping("/agent")
public class UpiAgentController {

	private static final Logger logger = LoggerFactory.getLogger(UpiAgentController.class);

	private final UpiFailureWorkflow workflow;

	public UpiAgentController(UpiFailureWorkflow workflow) {
		this.workflow = workflow;
	}

	/**
	 * POST /agent/upi-failure
	 *
	 * Accepts a NEW runtime transaction/failure. The transaction is not stored in RAG.
	 * Flow: RAG retrieval → Classification Agent → Incident Agent → Final Response
	 */
	@PostMapping(path = "/upi-failure", consumes = MediaType.APPLICATION_JSON_VALUE)
	public WorkflowResponse handleUpiFailure(@RequestBody TransactionFailureRequest transaction) {
		long start = System.currentTimeMillis();
		System.out.println("========== [/agent/upi-failure] REQUEST RECEIVED ==========");
		System.out.println("transactionId=" + transaction.transactionId()
				+ ", failureCode=" + transaction.failureCode()
				+ ", bank=" + transaction.bank());
		logger.info("POST /agent/upi-failure received txnId={} failureCode={}",
				transaction.transactionId(), transaction.failureCode());

		WorkflowResponse response = workflow.process(transaction);

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("========== [/agent/upi-failure] RESPONSE READY in " + elapsed + " ms ==========");
		System.out.println("incidentCreated="
				+ (response.incident() != null && response.incident().incidentCreated())
				+ ", incidentId="
				+ (response.incident() != null ? response.incident().incidentId() : null));
		logger.info("POST /agent/upi-failure completed in {} ms", elapsed);
		return response;
	}
}
