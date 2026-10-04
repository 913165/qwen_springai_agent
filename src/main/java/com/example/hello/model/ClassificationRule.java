package com.example.hello.model;

import java.util.List;

/**
 * One classification rule from classify.json.
 */
public record ClassificationRule(
		String failureCode,
		String failureType,
		String severity,
		String rootCause,
		String recommendedAction,
		boolean incidentRequired,
		String incidentCategory,
		List<String> matchHints
) {
}
