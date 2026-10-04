package com.example.hello.controller;

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
		return workflow.process(transaction);
	}
}
