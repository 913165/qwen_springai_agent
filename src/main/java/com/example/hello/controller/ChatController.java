package com.example.hello.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple chat endpoint against the local Qwen model (optional helper).
 * Main UPI workflow is POST /agent/upi-failure.
 */
@RestController
public class ChatController {

	private final ChatClient chatClient;

	public ChatController(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	@GetMapping("/chat")
	public String chat(@RequestParam(defaultValue = "Explain UPI failure code U30 in one short sentence.") String message) {
		return chatClient.prompt().user(message).call().content().trim();
	}
}
