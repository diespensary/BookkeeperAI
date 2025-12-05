package com.example.bookkeeperai.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedExpense {

    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private String place;
    private OffsetDateTime date;
}

