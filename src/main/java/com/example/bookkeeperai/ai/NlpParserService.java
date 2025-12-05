package com.example.bookkeeperai.ai;

import com.example.bookkeeperai.dto.ParsedExpense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NlpParserService {

    private final HuggingFaceClient hfClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedExpense parse(String userText) throws Exception {
        String prompt = buildPrompt(userText);
        String llmOutput = hfClient.generateText(prompt);

        // Логируем сырой ответ для отладки
        log.debug("LLM raw output: {}", llmOutput);

        // На всякий случай вырезаем JSON-объект из текста, если модель что-то дописала
        String cleaned = extractJsonObject(llmOutput);
        String trimmed = cleaned == null ? "" : cleaned.trim();

        if (trimmed.isEmpty()) {
            log.error("LLM вернул пустой/некорректный ответ: '{}'", llmOutput);
            throw new IllegalStateException("LLM вернул пустой ответ, JSON не найден");
        }

        // Ключевой момент: проверяем первый символ
        char first = trimmed.charAt(0);
        if (first != '{') {
            // Здесь как раз и будет случай с '<'
            log.error("Ожидал JSON-объект, но ответ начинается с '{}':\n{}",
                    first,
                    trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed);
            throw new IllegalStateException(
                    "Ожидал JSON, но получил текст, начинающийся с '" + first + "'");
        }

        JsonNode node;
        try {
            node = mapper.readTree(trimmed);
        } catch (Exception e) {
            log.error("Не удалось распарсить JSON из ответа LLM. cleaned={}", trimmed, e);
            throw e; // или new IllegalStateException("LLM вернул некорректный JSON", e);
        }

        ParsedExpense.ParsedExpenseBuilder builder = ParsedExpense.builder();

        // amount
        try {
            if (node.has("amount")) {
                builder.amount(new BigDecimal(node.get("amount").asText()));
            }
        } catch (Exception e) {
            log.warn("Cannot parse amount from LLM output: {}", node.get("amount"), e);
        }

        // currency
        if (node.has("currency") && !node.get("currency").isNull()) {
            builder.currency(node.get("currency").asText());
        }

        // category
        if (node.has("category") && !node.get("category").isNull()) {
            builder.category(node.get("category").asText());
        }

        // description
        if (node.has("description") && !node.get("description").isNull()) {
            builder.description(node.get("description").asText());
        }

        // place
        if (node.has("place") && !node.get("place").isNull()) {
            builder.place(node.get("place").asText());
        }

        // date
        OffsetDateTime date = OffsetDateTime.now();
        if (node.has("date") && !node.get("date").isNull()) {
            try {
                date = OffsetDateTime.parse(node.get("date").asText());
            } catch (DateTimeParseException e) {
                log.warn("Cannot parse date from LLM output: {}",
                        node.get("date").asText(), e);
            }
        }
        builder.date(date);

        return builder.build();
    }

    private String buildPrompt(String userText) {
        return """
            Ты парсер личных финансовых расходов. Пользователь пишет фразу на русском или английском
            о своей трате (сумма, валюта, категория, дата, комментарий, место покупки).

            Твоя задача — вернуть ОДИН JSON-объект БЕЗ каких-либо пояснений, строго в формате:

            {
              "amount": <число, через точку>,
              "currency": "<триграммный код валюты, например RUB, USD, EUR>",
              "category": "<одна из: groceries, transport, cafe, entertainment, bills, other>",
              "description": "<короткий комментарий>",
              "place": "<место траты, например 'Пятерочка у дома' или 'KFC на Тверской'>",
              "date": "<дата траты в ISO-8601, формат 2025-12-04T10:15:30+03:00>"
            }

            Если дата явно не указана, используй сегодняшнюю дату пользователя без времени.
            Текст пользователя: "%s"
            Верни ТОЛЬКО JSON, без текста до или после.
            """.formatted(userText);
    }

    private String extractJsonObject(String text) {
        String trimmed = text.trim();
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first != -1 && last != -1 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
    }
}

