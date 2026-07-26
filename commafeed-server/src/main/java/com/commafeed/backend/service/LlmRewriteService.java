package com.commafeed.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class LlmRewriteService {

    private final String llmUrl;
    private final String llmApiKey;
    private final String llmModel;
    private final long timeoutSeconds;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Inject
    public LlmRewriteService(
            @ConfigProperty(name = "app.llm.url") String llmUrl,
            @ConfigProperty(name = "app.llm.api-key") String llmApiKey,
            @ConfigProperty(name = "app.llm.model") String llmModel,
            @ConfigProperty(name = "app.llm.timeout-seconds", defaultValue = "15")
                    long timeoutSeconds,
            ObjectMapper objectMapper) {
        this.llmUrl = llmUrl;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String rewrite(String originalText, String promptInstructions) {
        try {
            String payload = buildOpenAiPayload(originalText, promptInstructions);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(llmUrl))
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + llmApiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error(
                        "LLM API error. Status: {}, Body: {}",
                        response.statusCode(),
                        response.body());
                throw new WebApplicationException(
                        "Failed to generate alternative content via LLM",
                        Response.Status.BAD_GATEWAY);
            }

            return parseOpenAiResponse(response.body());

        } catch (WebApplicationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("LLM request interrupted", e);
            throw new WebApplicationException(
                    "LLM request was interrupted", Response.Status.GATEWAY_TIMEOUT);
        } catch (Exception e) {
            log.error("LLM request failed", e);
            throw new WebApplicationException(
                    "Internal error during LLM generation", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildOpenAiPayload(String originalText, String promptInstructions) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", llmModel);

        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put(
                "content",
                "You are an assistant that rewrites text based on user instructions. Output ONLY the rewritten text, without any conversational filler, explanations, or quotes.");

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put(
                "content",
                "Instruction: " + promptInstructions + "\n\nOriginal Text:\n" + originalText);

        return root.toString();
    }

    private String parseOpenAiResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode choices = rootNode.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText().trim();
        }
        throw new RuntimeException("Invalid response format from LLM");
    }
}
