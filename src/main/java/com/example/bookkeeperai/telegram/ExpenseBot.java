package com.example.bookkeeperai.telegram;

import com.example.bookkeeperai.entity.Expense;
import com.example.bookkeeperai.service.ExpenseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

@Component
@Slf4j
public class ExpenseBot extends TelegramLongPollingBot {

    private final String username;
    private final String token;
    private final ExpenseService expenseService;

    public ExpenseBot(@Value("${telegram.bot.username}") String username,
                      @Value("${telegram.bot.token}") String token,
                      ExpenseService expenseService) {
        this.username = username;
        this.token = token;
        this.expenseService = expenseService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message msg = update.getMessage();

                if (msg.hasText() && msg.getText().startsWith("/")) {
                    handleCommand(msg);
                } else if (msg.hasText()) {
                    handleText(msg);
                } else if (msg.hasVoice()) {
                    handleVoice(msg);
                }
            }
        } catch (Exception e) {
            log.error("Error while processing update", e);
            if (update.hasMessage()) {
                sendSimpleMessage(update.getMessage().getChatId(),
                        "Произошла ошибка: " + e.getMessage());
            }
        }
    }

    private void handleCommand(Message msg) throws Exception {
        String text = msg.getText();
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        switch (text) {
            case "/start" -> sendSimpleMessage(chatId,
                    """
                    Привет! Я бот для учета расходов.
                    Просто напиши: "вчера в магните потратил 500 руб на продукты"
                    или отправь голосовое, а я сам распарсю и запишу.

                    Команды:
                    /last - показать последние 10 расходов
                    """);
            case "/last" -> {
                List<Expense> last = expenseService.getLastExpenses(userId);
                if (last.isEmpty()) {
                    sendSimpleMessage(chatId, "Пока расходов нет.");
                } else {
                    StringBuilder sb = new StringBuilder("Последние расходы:\n");
                    last.forEach(e -> sb.append("- ")
                            .append(e.getExpenseDate() != null ? e.getExpenseDate().toLocalDate() : "")
                            .append(" | ")
                            .append(e.getAmount()).append(" ").append(e.getCurrency())
                            .append(" | ").append(e.getCategory())
                            .append(" | ").append(
                                    e.getDescription() != null ? e.getDescription() : "")
                            .append(" | ").append(
                                    e.getPlace() != null ? e.getPlace() : "")
                            .append("\n"));
                    sendSimpleMessage(chatId, sb.toString());
                }
            }
            default -> sendSimpleMessage(chatId, "Неизвестная команда.");
        }
    }

    private void handleText(Message msg) throws Exception {
        Long chatId = msg.getChatId();
        Expense expense = expenseService.handleTextExpense(msg);
        sendSimpleMessage(chatId,
                "Записал расход: " + expense.getAmount() + " " + expense.getCurrency()
                        + " (" + expense.getCategory()
                        + (expense.getPlace() != null ? ", место: " + expense.getPlace() : "")
                        + ")");
    }

    private void handleVoice(Message msg) throws Exception {
        Long chatId = msg.getChatId();

        Voice voice = msg.getVoice();
        String fileId = voice.getFileId();

        // 1. Получаем файл от Telegram
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

        String fileUrl = file.getFileUrl(getBotToken());

        // 2. Скачиваем байты
        try (InputStream in = new URL(fileUrl).openStream()) {
            byte[] audioBytes = in.readAllBytes();

            // 3. Обрабатываем
            Expense expense = expenseService.handleVoiceExpense(msg, audioBytes);

            sendSimpleMessage(chatId,
                    "Распознал и записал расход: " + expense.getAmount() + " " + expense.getCurrency()
                            + " (" + expense.getCategory()
                            + (expense.getPlace() != null ? ", место: " + expense.getPlace() : "")
                            + ")");
        }
    }

    private void sendSimpleMessage(Long chatId, String text) {
        try {
            SendMessage sm = new SendMessage(chatId.toString(), text);
            execute(sm);
        } catch (Exception e) {
            log.error("Error while sending message", e);
        }
    }
}

