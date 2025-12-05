package com.example.bookkeeperai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@Slf4j
public class HuggingFaceClient {

    private static final String HF_ASR_BASE =
            "https://router.huggingface.co/hf-inference/models/";
    private static final String HF_CHAT_URL =
            "https://router.huggingface.co/v1/chat/completions";

    private final String hfToken;
    private final String sttModel;
    private final String nlpModel;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public HuggingFaceClient(
            @Value("${huggingface.token}") String hfToken,
            @Value("${huggingface.sttModel}") String sttModel,
            @Value("${huggingface.nlpModel}") String nlpModel
    ) {
        this.hfToken = hfToken;
        this.sttModel = sttModel;
        this.nlpModel = nlpModel;
    }

    /** Общая проверка, что ответ действительно JSON, а не HTML. */
    private void ensureJson(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        String contentType = response.headers()
                .firstValue("content-type")
                .orElse("");

        // Если это не application/json ИЛИ тело начинается с '<' — считаем, что это не JSON.
        if (!contentType.toLowerCase().startsWith("application/json")
                || (!body.isBlank() && body.trim().charAt(0) == '<')) {

            log.error(
                    "HF вернул не JSON.\nstatus={} contentType={}\nbody:\n{}",
                    response.statusCode(), contentType, body
            );
            throw new IllegalStateException(
                    "HuggingFace вернул не JSON (content-type: " + contentType + ")"
            );
        }
    }

    /**
     * Speech-to-text через HF ASR (Whisper).
     */
    public String speechToText(byte[] audioBytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_ASR_BASE + sttModel))
                .header("Authorization", "Bearer " + hfToken)
                // Telegram voice = .ogg (Opus)
                .header("Content-Type", "audio/ogg")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();

        if (status >= 400) {
            log.error("HF STT HTTP error: {} body: {}", status, response.body());
            throw new IllegalStateException(
                    "HF STT HTTP error: " + status + " body: " + response.body()
            );
        }

        ensureJson(response);

        JsonNode root = mapper.readTree(response.body());
        if (root.has("text")) {
            return root.get("text").asText();
        }

        log.warn("HF STT: unexpected response format: {}", response.body());
        return response.body();
    }


    /**
     * LLM через OpenAI-совместимый Chat Completions.
     */
    public String generateText(String prompt) throws Exception {
        ObjectNode bodyNode = mapper.createObjectNode();
        bodyNode.put("model", nlpModel);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        bodyNode.set("messages", messages);

        bodyNode.put("max_tokens", 512);
        bodyNode.put("temperature", 0.1);

        // 👇 добавляем требование вернуть JSON-объект
        ObjectNode responseFormat = bodyNode.putObject("response_format");
        responseFormat.put("type", "json_object");

        String json = mapper.writeValueAsString(bodyNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_CHAT_URL))
                .header("Authorization", "Bearer " + hfToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();

        if (status >= 400) {
            log.error("HF LLM HTTP error: {} body: {}", status, response.body());
            throw new IllegalStateException(
                    "HF LLM HTTP error: " + status + " body: " + response.body()
            );
        }

        ensureJson(response);

        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            if (message.has("content")) {
                return message.get("content").asText();
            }
        }

        log.warn("Unexpected HF LLM response format: {}", response.body());
        return response.body();
    }
}
