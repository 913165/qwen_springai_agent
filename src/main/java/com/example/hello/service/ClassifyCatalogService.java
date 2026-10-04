package com.example.hello.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.hello.model.ClassificationRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads classify.json and resolves classification rules.
 * RAG finds knowledge; this catalog decides the official classification.
 */
@Service
public class ClassifyCatalogService {

	private final List<ClassificationRule> rules;
	private final ClassificationRule fallback;

	public ClassifyCatalogService() throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		try (InputStream in = new ClassPathResource("classify.json").getInputStream()) {
			JsonNode root = objectMapper.readTree(in);
			List<ClassificationRule> loaded = new ArrayList<>();
			for (JsonNode node : root.path("classifications")) {
				loaded.add(toRule(node));
			}
			this.rules = List.copyOf(loaded);
			this.fallback = toRule(root.path("fallback"));
		}
		System.out.println("[ClassifyCatalog] loaded " + rules.size()
				+ " rules from classify.json (fallback=" + fallback.failureCode() + ")");
	}

	public List<ClassificationRule> getRules() {
		return rules;
	}

	public ClassificationRule getFallback() {
		return fallback;
	}

	/**
	 * Exact match by failure code from classify.json.
	 */
	public Optional<ClassificationRule> findByFailureCode(String failureCode) {
		if (!StringUtils.hasText(failureCode)) {
			return Optional.empty();
		}
		String code = failureCode.trim().toUpperCase(Locale.ROOT);
		return rules.stream()
				.filter(r -> r.failureCode().equalsIgnoreCase(code))
				.findFirst();
	}

	/**
	 * Try to pick a classify.json rule using RAG snippets + runtime failure code.
	 */
	public Optional<ClassificationRule> resolveFromRagAndCode(String failureCode, List<String> ragDocs) {
		Optional<ClassificationRule> byCode = findByFailureCode(failureCode);
		if (byCode.isPresent()) {
			return byCode;
		}
		if (ragDocs == null) {
			return Optional.empty();
		}
		for (String doc : ragDocs) {
			if (!StringUtils.hasText(doc)) {
				continue;
			}
			String upper = doc.toUpperCase(Locale.ROOT);
			for (ClassificationRule rule : rules) {
				String code = rule.failureCode().toUpperCase(Locale.ROOT);
				if (upper.contains("\"FAILURE_CODE\": \"" + code + "\"")
						|| upper.contains("\"FAILURE_CODE\":\"" + code + "\"")) {
					return Optional.of(rule);
				}
			}
		}
		return Optional.empty();
	}

	private static ClassificationRule toRule(JsonNode node) {
		List<String> hints = new ArrayList<>();
		if (node.has("matchHints") && node.get("matchHints").isArray()) {
			node.get("matchHints").forEach(h -> hints.add(h.asText()));
		}
		return new ClassificationRule(
				text(node, "failureCode"),
				text(node, "failureType"),
				text(node, "severity"),
				text(node, "rootCause"),
				text(node, "recommendedAction"),
				node.path("incidentRequired").asBoolean(true),
				text(node, "incidentCategory"),
				List.copyOf(hints));
	}

	private static String text(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return v == null || v.isNull() ? "" : v.asText();
	}
}
