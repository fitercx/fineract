package com.crediblex.fineract.portfolio.loc.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class LineOfCreditTransactionData implements Serializable {

    private final Long id;
    private final Long lineOfCreditId;
    private final String transactionType;
    private final BigDecimal amount;
    private final BigDecimal balanceBefore;
    private final BigDecimal balanceAfter;
    private final OffsetDateTime transactionDate;
    private final String referenceNumber;
    private final String description;
    private final OffsetDateTime createdOn;
    private final String createdBy;
}