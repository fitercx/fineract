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

package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Entity to track Line of Credit transaction history for audit and traceability. This class records all LOC-affecting
 * events with timestamp, user, and action metadata.
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
    @Enumerated(value = EnumType.STRING)
    private LineOfCreditTransactionType transactionType;

    @Column(name = "amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", precision = 19, scale = 6, nullable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 19, scale = 6, nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_backdated_entry", nullable = false)
    private Boolean isBackdatedEntry = false;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "loan_transaction_id")
    private Long loanTransactionId;

    @Column(name = "consumed_amount_before", precision = 19, scale = 6)
    private BigDecimal consumedAmountBefore;

    @Column(name = "consumed_amount_after", precision = 19, scale = 6)
    private BigDecimal consumedAmountAfter;

    /**
     * Default constructor.
     */
    protected LineOfCreditTransaction() {}

    public LineOfCreditTransaction(LineOfCredit lineOfCredit, LineOfCreditTransactionType transactionType, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate, String referenceNumber, String description) {
        this.lineOfCredit = lineOfCredit;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionDate = transactionDate;
        this.referenceNumber = referenceNumber;
        this.description = description;
    }

    /**
     * Static factory method for creating a disbursement transaction record.
     *
     * @param lineOfCredit
     *            the line of credit
     * @param transactionAmount
     *            the transaction amount
     * @param balanceBefore
     *            the balance before disbursement
     * @param balanceAfter
     *            the balance after disbursement
     * @param transactionDate
     *            the transaction date
     * @param loanReference
     *            the loan reference number
     * @return a new LOC transaction record
     */
    private static LineOfCreditTransaction createTransaction(LineOfCredit lineOfCredit, BigDecimal transactionAmount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate, String loanReference,
            LineOfCreditTransactionType type, String description) {
        return new LineOfCreditTransaction(lineOfCredit, type, transactionAmount, balanceBefore, balanceAfter, transactionDate,
                loanReference, description);
    }

    private static String getTransactionDescription(LineOfCreditTransactionType type) {
        return switch (type) {
            case DISBURSEMENT -> "Loan disbursement - LOC balance reduced";
            case REPAYMENT -> "Loan repayment - LOC balance increased";
            case FORECLOSURE -> "Refund for early repayment";
            case REFUND, REVERSAL -> "Loan repayment payment refund/reversal - LOC balance decreased";
            case INCREMENT -> "Increment Line of Credit limit - LOC balance increased";
            case DECREMENT -> "Decrease of Line of Credit limit - LOC balance decreased";
            case UNDO_DISBURSEMENT -> "Increment after undo disbursement. LOC balance increased";
            case BLOCK -> "Block amount from Line of Credit - LOC available balance reduced";
            case UNBLOCK -> "Unblock amount from Line of Credit - LOC available balance increased";
            case WRITE_OFF -> "Write off - LOC balance adjusted";
            default -> throw new IllegalArgumentException("Unsupported transaction type: " + type);
        };
    }

    public static LineOfCreditTransaction newTransactionInstance(LineOfCredit lineOfCredit, BigDecimal transactionAmount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate, String loanReference,
            LineOfCreditTransactionType type) {
        return createTransaction(lineOfCredit, transactionAmount, balanceBefore, balanceAfter, transactionDate, loanReference, type,
                getTransactionDescription(type));
    }

    public boolean isDisbursement() {
        return this.transactionType == LineOfCreditTransactionType.DISBURSEMENT;
    }
}
