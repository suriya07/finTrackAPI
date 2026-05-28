package com.example.financemanager.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Total spend (or income) grouped under a single category. */
public record CategorySpendDTO(
        UUID categoryId,
        String categoryName,
        BigDecimal total) {
}
