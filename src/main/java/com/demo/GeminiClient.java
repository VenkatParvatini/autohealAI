package com.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal client for Google Gemini's generateContent REST endpoint.
 * No SDK needed — just a plain HTTP POST with a JSON body.
 */
public class GeminiClient {

    // CONFIRMED WORKING (Sept 2026, on a newly-created API key): gemini-3.5-flash-lite
    // Google's model availability shifts over time and can differ by key age/tier —
    // if this 404s again later, check https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY
    // and try whichever current "-flash" or "-flash-lite" model the error message recommends.
    private static final String MODEL_NAME = "gemini-3.5-flash-lite";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Sends the prompt to Gemini and returns the model's raw text reply
     * (which, per our prompt instructions, should be a JSON string).
     */
    public String ask(String prompt) throws Exception {

        // Build the request body Gemini expects: { "contents": [ { "parts": [ { "text": "..." } ] } ] }
        String escapedPrompt = mapper.writeValueAsString(prompt); // safely escapes quotes/newlines
        String requestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        { "text": %s }
                      ]
                    }
                  ]
                }
                """.formatted(escapedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API call failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        // Dig the actual text reply out of Gemini's response envelope:
        // { "candidates": [ { "content": { "parts": [ { "text": "..." } ] } } ] }
        JsonNode root = mapper.readTree(response.body());
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();
    }
}
