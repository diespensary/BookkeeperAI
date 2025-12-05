package com.example.bookkeeperai.service;

import com.example.bookkeeperai.ai.NlpParserService;
import com.example.bookkeeperai.ai.SttService;
import com.example.bookkeeperai.dto.ParsedExpense;
import com.example.bookkeeperai.entity.Expense;
import com.example.bookkeeperai.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository repo;
    private final NlpParserService nlpParser;
    private final SttService sttService;

    public Expense handleTextExpense(Message message) throws Exception {
        Long userId = message.getFrom().getId();
        String text = message.getText();

        ParsedExpense parsed = nlpParser.parse(text);
        return saveParsedExpense(parsed, userId, text, "TEXT");
    }

    public Expense handleVoiceExpense(Message message, byte[] audioBytes) throws Exception {
        Long userId = message.getFrom().getId();
        String transcript = sttService.transcribe(audioBytes);

        ParsedExpense parsed = nlpParser.parse(transcript);
        return saveParsedExpense(parsed, userId, transcript, "VOICE");
    }

    private Expense saveParsedExpense(ParsedExpense parsed,
                                      Long userId,
                                      String rawText,
                                      String sourceType) {

        Expense.ExpenseBuilder builder = Expense.builder()
                .telegramUserId(userId)
                .amount(parsed.getAmount() != null ? parsed.getAmount() : BigDecimal.ZERO)
                .currency(parsed.getCurrency() != null ? parsed.getCurrency() : "RUB")
                .category(parsed.getCategory() != null ? parsed.getCategory() : "other")
                .description(parsed.getDescription())
                .place(parsed.getPlace())
                .expenseDate(parsed.getDate() != null ? parsed.getDate() : OffsetDateTime.now())
                .rawText(rawText)
                .sourceType(sourceType);

        return repo.save(builder.build());
    }

    public List<Expense> getLastExpenses(Long userId) {
        return repo.findTop10ByTelegramUserIdOrderByExpenseDateDesc(userId);
    }
}

