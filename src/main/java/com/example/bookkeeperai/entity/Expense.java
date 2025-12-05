package com.example.bookkeeperai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramUserId;

    private BigDecimal amount;

    private String currency;      // "RUB", "USD", "EUR"...

    private String category;      // "groceries", "transport", ...

    private String description;   // произвольный комментарий

    private String place;         // место траты: "Пятёрочка у дома", "KFC на Тверской"

    private OffsetDateTime expenseDate;

    @Lob
    private String rawText;       // исходный текст/расшифровка голоса

    private String sourceType;    // "TEXT" или "VOICE"
}

