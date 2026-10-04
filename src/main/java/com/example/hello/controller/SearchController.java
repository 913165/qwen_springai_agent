package com.example.hello.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Semantic search over generic UPI failure knowledge stored in pgvector.
 *
 * Examples:
 *   GET /search?query=U30
 *   GET /search/console?query=U30%20remitter%20bank%20timeout
 */
@RestController
public class SearchController {

	private final VectorStore vectorStore;

	public SearchController(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@GetMapping("/search")
	public List<Map<String, Object>> search(
			@RequestParam String query,
			@RequestParam(defaultValue = "3") int topK) {
		return runSearch(query, topK, false);
	}

	/**
	 * Same as /search but prints ranked matches to the application console.
	 */
	@GetMapping("/search/console")
	public List<Map<String, Object>> searchWithConsole(
			@RequestParam String query,
			@RequestParam(defaultValue = "5") int topK) {
		return runSearch(query, topK, true);
	}

	private List<Map<String, Object>> runSearch(String query, int topK, boolean printConsole) {
		long start = System.currentTimeMillis();
		if (printConsole) {
			System.out.println("========== [/search/console] QUERY ==========");
			System.out.println("query=" + query + ", topK=" + topK);
		}

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(query)
						.topK(topK)
						.build());

		List<Map<String, Object>> payload = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			Document doc = results.get(i);
			double score = doc.getScore() != null ? doc.getScore() : 0.0;
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("rank", i + 1);
			row.put("id", doc.getId());
			row.put("score", score);
			row.put("content", doc.getText());
			row.put("metadata", doc.getMetadata());
			payload.add(row);

			if (printConsole) {
				String preview = doc.getText();
				if (preview != null && preview.length() > 220) {
					preview = preview.substring(0, 220) + "...";
				}
				System.out.println("match[" + (i + 1) + "] score=" + score);
				System.out.println("  " + preview);
			}
		}

		long elapsed = System.currentTimeMillis() - start;
		if (printConsole) {
			System.out.println("========== [/search/console] DONE in " + elapsed + " ms, matches="
					+ payload.size() + " ==========");
		}
		return payload;
	}
}
