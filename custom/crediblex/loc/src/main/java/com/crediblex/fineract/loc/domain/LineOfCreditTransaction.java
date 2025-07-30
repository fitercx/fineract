/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.loc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Line of Credit Transaction entity for tracking balance changes.
 */
@Entity
@Table(name = "m_line_of_credit_transactions")
@Getter
@Setter
public class LineOfCreditTransaction extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_of_credit_id", nullable = false)
    private LineOfCredit lineOfCredit;

    @Column(name = "transaction_type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", precision = 19, scale = 6, nullable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 19, scale = 6, nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Default constructor.
     */
    protected LineOfCreditTransaction() {
        // Required by JPA
    }

    /**
     * Constructor for creating a new Line of Credit Transaction.
     */
    public LineOfCreditTransaction(LineOfCredit lineOfCredit, TransactionType transactionType,
                                 BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                 String referenceNumber, String description) {
        this.lineOfCredit = lineOfCredit;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionDate = LocalDateTime.now();
        this.referenceNumber = referenceNumber;
        this.description = description;
    }

    /**
     * Transaction type enum for Line of Credit transactions.
     */
    public enum TransactionType {
        DRAW, REPAYMENT, FEE, ADJUSTMENT, ACTIVATION, DEACTIVATION
    }
} 