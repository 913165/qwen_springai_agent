package com.example.hello;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class SpringAiHelloApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiHelloApplication.class);

	private final VectorStore vectorStore;

	@Value("classpath:/input.txt")
	private Resource resource;

	public SpringAiHelloApplication(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringAiHelloApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Loading documents into vector store from " + resource.getFilename() + "...");
		long startMs = System.currentTimeMillis();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
				 Stream<String> lines = reader.lines()) {
			List<Document> documents = lines.map(Document::new).toList();
			int batchSize = 3;
			int loadedSoFar = 0;
			for (int i = 0; i < documents.size(); i += batchSize) {
				List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
				vectorStore.add(batch);
				loadedSoFar += batch.size();
				logger.info("Iteration {}: loaded {} documents so far (batch size {})", (i / batchSize) + 1, loadedSoFar, batch.size());
				System.out.println("Iteration " + ((i / batchSize) + 1) + ": loaded " + loadedSoFar + " documents so far (batch size " + batch.size() + ")");
			}
			long elapsedMs = System.currentTimeMillis() - startMs;
			logger.info("Loaded {} documents into vector store in {} ms ({} s)", documents.size(), elapsedMs, elapsedMs / 1000.0);
			System.out.println("Loaded " + documents.size() + " documents into vector store in " + elapsedMs + " ms (" + (elapsedMs / 1000.0) + " s)");
		}
		catch (Exception ex) {
			long elapsedMs = System.currentTimeMillis() - startMs;
			ex.printStackTrace();
			logger.warn("Skipping vector store startup load after {} ms: {}", elapsedMs, ex.getMessage());
			System.out.println("Skipping vector store startup load after " + elapsedMs + " ms: " + ex.getMessage());
		}
	}

}
