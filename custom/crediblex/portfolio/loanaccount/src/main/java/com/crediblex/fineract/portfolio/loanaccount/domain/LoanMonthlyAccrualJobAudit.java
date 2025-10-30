/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loanaccount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Represents an audit record for a monthly loan accrual job run.
 * <p>
 * This entity is mapped to the database table {@code m_loan_monthly_accrual_job_run_audit} and is used to track the
 * processing of loan accrual transactions during scheduled monthly accrual jobs. Each record captures the loan
 * involved, the set of accrual transaction IDs processed, the total interest accrued, whether the results have been
 * posted to Odoo (an external accounting system), and the date the job was generated.
 * <p>
 * Key fields:
 * <ul>
 * <li>{@code loanId} - The unique identifier of the loan for which accruals are processed.</li>
 * <li>{@code accrualTransactionIds} - Comma-separated list of transaction IDs generated during the accrual job.</li>
 * <li>{@code totalInterestAccrualDerived} - The total interest amount accrued for the loan in this job run.</li>
 * <li>{@code postedToOdoo} - Indicates if the accrual data has been posted to Odoo.</li>
 * <li>{@code generatedOnDate} - The date when this accrual job run was generated.</li>
 * </ul>
 * <p>
 * This entity supports auditability and traceability of monthly accrual job executions for loans.
 */
@Getter
@Entity
@Table(name = "m_loan_monthly_accrual_job_run_audit")
@NoArgsConstructor
public final class LoanMonthlyAccrualJobAudit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "accrual_transaction_ids")
    private String accrualTransactionIds;

    @Column(name = "total_interest_accrual_derived")
    private BigDecimal totalInterestAccrualDerived;

    @Column(name = "is_posted_to_odoo")
    private boolean postedToOdoo;

    @Column(name = "generated_on_date")
    private LocalDate generatedOnDate;

    private LoanMonthlyAccrualJobAudit(final Long loanId, final String accrualTransactionIds, final BigDecimal totalInterestAccrualDerived,
            final boolean postedToOdoo, final LocalDate generatedOnDate) {
        this.loanId = loanId;
        this.accrualTransactionIds = accrualTransactionIds;
        this.totalInterestAccrualDerived = totalInterestAccrualDerived;
        this.postedToOdoo = postedToOdoo;
        this.generatedOnDate = generatedOnDate;
    }

    public static LoanMonthlyAccrualJobAudit createNew(final Long loanId, final String accrualTransactionIds,
            final BigDecimal totalInterestAccrualDerived, final boolean postedToOdoo, final LocalDate generatedOnDate) {
        return new LoanMonthlyAccrualJobAudit(loanId, accrualTransactionIds, totalInterestAccrualDerived, postedToOdoo, generatedOnDate);
    }

    public void setPostedToOdoo(final boolean postedToOdoo) {
        this.postedToOdoo = postedToOdoo;
    }
}
