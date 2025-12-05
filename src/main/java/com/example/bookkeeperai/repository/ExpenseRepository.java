package com.example.bookkeeperai.repository;

import com.example.bookkeeperai.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findTop10ByTelegramUserIdOrderByExpenseDateDesc(Long userId);

    List<Expense> findByTelegramUserIdAndExpenseDateBetween(
            Long userId,
            OffsetDateTime from,
            OffsetDateTime to
    );
}

