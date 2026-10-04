package com.example.hello.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.hello.model.ClassificationResult;

/**
 * Agent 1 tool — save the chosen classification from classify.json.
 */
@Component
public class ClassifyFailureTool {

	private volatile ClassificationResult lastResult;

	@Tool(name = "classifyFailure", description = """
			Save the final UPI failure classification chosen from classify.json.
			Pass values from the matched classify.json entry (or OTHER fallback).
			Call exactly once.
			""")
	public String classifyFailure(
			@ToolParam(description = "Runtime transaction ID") String transactionId,
			@ToolParam(description = "failureCode from classify.json, or OTHER") String failureCode,
			@ToolParam(description = "failureType from classify.json") String failureType,
			@ToolParam(description = "severity from classify.json") String severity,
			@ToolParam(description = "rootCause from classify.json") String rootCause,
			@ToolParam(description = "incidentRequired from classify.json") boolean incidentRequired,
			@ToolParam(description = "incidentCategory from classify.json") String incidentCategory,
			@ToolParam(description = "recommendedAction from classify.json") String recommendedAction) {

		lastResult = new ClassificationResult(
				transactionId,
				failureCode,
				failureType,
				severity,
				rootCause,
				incidentRequired,
				incidentCategory,
				recommendedAction);

		System.out.println("[TOOL] classifyFailure CALLED");
		System.out.println("       code=" + failureCode
				+ ", type=" + failureType
				+ ", incidentRequired=" + incidentRequired);

		return "Saved classification from catalog: " + failureCode + " / " + failureType;
	}

	public ClassificationResult getLastResult() {
		return lastResult;
	}

	public void clear() {
		lastResult = null;
	}
}
