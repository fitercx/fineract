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
package com.crediblex.fineract.portfolio.loanaccount.jobs.generateloanmonthlyaccrualsummations;

import com.crediblex.fineract.portfolio.loanaccount.data.LoanMonthlyAccrualData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAudit;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAuditRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

@Slf4j
@RequiredArgsConstructor
public class GenerateLoanMonthlyAccrualSummationsTasklet implements Tasklet {

    private final LoanMonthlyAccrualJobAuditRepository loanMonthlyAccrualJobAuditRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        final Collection<LoanMonthlyAccrualData> LoanMonthlyAccrualList = this.retrieveLoanMonthlyAccrualSummaries();
        log.info("Total Loan Monthly Accrual Summations to be processed: {}", LoanMonthlyAccrualList.size());
        if (!LoanMonthlyAccrualList.isEmpty()) {
            final List<Throwable> exceptions = new ArrayList<>();
            final LocalDate generatedOnDate = DateUtils.getBusinessLocalDate();
            for (final LoanMonthlyAccrualData loanMonthlyAccrualData : LoanMonthlyAccrualList) {
                log.info("Processing Loan Monthly Accrual Summation for Loan ID: {} with Total Interest Accrual Derived: {}",
                        loanMonthlyAccrualData.getLoanId(), loanMonthlyAccrualData.getTotalInterestAccrualDerived());
                try {
                    final LoanMonthlyAccrualJobAudit loanMonthlyAccrualJobAudit = LoanMonthlyAccrualJobAudit.createNew(
                            loanMonthlyAccrualData.getLoanId(), loanMonthlyAccrualData.getAccrualTransactionIds(),
                            loanMonthlyAccrualData.getTotalInterestAccrualDerived(), false, generatedOnDate);
                    this.loanMonthlyAccrualJobAuditRepository.saveAndFlush(loanMonthlyAccrualJobAudit);
                    this.updateLoanTransactionsAsProcessed(loanMonthlyAccrualData.getAccrualTransactionIds());
                    log.info("Successfully processed Loan Monthly Accrual Summation for Loan ID: {} ", loanMonthlyAccrualData.getLoanId());
                } catch (final Exception e) {
                    log.error("Error while processing monthly accrual summations for loan with ID {} ", loanMonthlyAccrualData.getLoanId(),
                            e);
                    exceptions.add(e);
                }
            }
            if (!exceptions.isEmpty()) {
                throw new JobExecutionException(exceptions);
            }
        }
        return RepeatStatus.FINISHED;
    }

    private void updateLoanTransactionsAsProcessed(final String accrualTransactionIds) {
        final Long[] loanIDs = Stream.of(accrualTransactionIds.split(",")).map(Long::valueOf).toArray(Long[]::new);
        final String placeholders = String.join(",", Collections.nCopies(loanIDs.length, "?"));
        final String sql = "UPDATE m_loan_transaction SET is_processed_by_monthly_job = TRUE WHERE id IN (" + placeholders + ")";
        this.jdbcTemplate.update(sql, (Object[]) loanIDs);
    }

    private List<LoanMonthlyAccrualData> retrieveLoanMonthlyAccrualSummaries() {
        final String sql = """
                SELECT
                    mlt.loan_id AS "loanId",
                    SUM(mlt.interest_portion_derived) AS "totalInterestAccrualDerived",
                    STRING_AGG(mlt.id::TEXT, ',' ORDER BY mlt.id) AS "accrualTransactionIds"
                FROM m_loan_transaction mlt
                WHERE mlt.transaction_type_enum = 10
                    AND COALESCE(mlt.interest_portion_derived, 0) > 0
                    AND mlt.is_reversed = FALSE
                    AND COALESCE(mlt.is_processed_by_monthly_job, FALSE) = FALSE
                GROUP BY mlt.loan_id
                ORDER BY mlt.loan_id
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> LoanMonthlyAccrualData.builder().loanId(rs.getLong("loanId"))
                        .accrualTransactionIds(rs.getString("accrualTransactionIds"))
                        .totalInterestAccrualDerived(rs.getBigDecimal("totalInterestAccrualDerived")).build());
    }
}
