package com.example.hello.controller;

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
 * Example: GET /search?query=U30%20remitter%20bank%20timeout
 */
@RestController
public class SearchController {

	private final VectorStore vectorStore;

	public SearchController(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@GetMapping("/search")
	public List<Map<String, Object>> search(@RequestParam String query) {
		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(query)
						.topK(3)
						.build());

		return results.stream()
				.map(doc -> Map.<String, Object>of(
						"id", doc.getId(),
						"content", doc.getText(),
						"score", doc.getScore() != null ? doc.getScore() : 0.0,
						"metadata", doc.getMetadata()))
				.toList();
	}
}
