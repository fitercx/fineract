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

package com.crediblex.fineract.portfolio.loanaccount.repository;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomLoanChargeRepository extends JpaRepository<LoanCharge, Long> {

    @Query("""
                SELECT lc FROM LoanCharge lc
                LEFT JOIN FETCH lc.loan l
                LEFT JOIN FETCH lc.charge c
                LEFT JOIN FETCH lc.loanTrancheDisbursementCharge dc
                LEFT JOIN FETCH dc.loanDisbursementDetails dd
                WHERE lc.loan.id = :loanId AND lc.active = true
                ORDER BY
                    COALESCE(lc.dueDate, COALESCE(dd.actualDisbursementDate, dd.expectedDisbursementDate)),
                    lc.chargeTime ASC,
                    lc.dueDate ASC,
                    lc.penaltyCharge ASC
            """)
    List<LoanCharge> findActiveByLoanIdWithOrder(@Param("loanId") Long loanId);

    /**
     * Find all loan charges (including inactive ones) for a loan, ordered by due date and charge time. This method is
     * used when we need to display reversed charges or all charges regardless of active status.
     */
    @Query("""
                SELECT lc FROM LoanCharge lc
                LEFT JOIN FETCH lc.loan l
                LEFT JOIN FETCH lc.charge c
                LEFT JOIN FETCH lc.loanTrancheDisbursementCharge dc
                LEFT JOIN FETCH dc.loanDisbursementDetails dd
                WHERE lc.loan.id = :loanId
                ORDER BY
                    COALESCE(lc.dueDate, COALESCE(dd.actualDisbursementDate, dd.expectedDisbursementDate)),
                    lc.chargeTime ASC,
                    lc.dueDate ASC,
                    lc.penaltyCharge ASC
            """)
    List<LoanCharge> findAllByLoanIdWithOrder(@Param("loanId") Long loanId);

    @Query("""
            SELECT DISTINCT lt FROM LoanTransaction lt
            INNER JOIN lt.loanChargesPaid lcpb
            WHERE lcpb.loanCharge.id IN :chargeIds
            AND lt.typeOf = :transactionType
            AND lt.reversed = false
            """)
    List<LoanTransaction> findAccrualTransactionsByChargeIds(@Param("chargeIds") List<Long> chargeIds,
            @Param("transactionType") LoanTransactionType transactionType);

    @Query("""
            SELECT lc FROM LoanCharge lc
            WHERE lc.loan.id = :loanId
            AND lc.dueDate >= :fromDate
            AND lc.chargeTime = :chargeTimeValue
            """)
    List<LoanCharge> findByLoanIdAndFromDueDate(@Param("loanId") Long loanId, @Param("fromDate") LocalDate fromDate,
            @Param("chargeTimeValue") Integer chargeTimeValue);

    @Query("""
            SELECT lc FROM LoanCharge lc
            WHERE lc.loan.id = :loanId
            AND lc.dueDate = :dueDate
            AND lc.chargeTime = :chargeTimeValue
            """)
    List<LoanCharge> findByLoanIdAndExactDueDate(@Param("loanId") Long loanId, @Param("dueDate") LocalDate dueDate,
            @Param("chargeTimeValue") Integer chargeTimeValue);

    @Query("""
            SELECT lc FROM LoanCharge lc
            WHERE lc.loan.id = :loanId
            AND lc.dueDate >= :fromDate
            AND lc.dueDate <= :toDate
            AND lc.chargeTime = :chargeTimeValue
            """)
    List<LoanCharge> findByLoanIdAndDueDateRange(@Param("loanId") Long loanId, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate, @Param("chargeTimeValue") Integer chargeTimeValue);

    @Query("""
            SELECT lc FROM LoanCharge lc
            WHERE lc.loan.id = :loanId
            AND lc.active = true
            AND lc.chargeTime = :chargeTimeValue
            """)
    List<LoanCharge> findAllActiveOverdueChargesByLoanId(@Param("loanId") Long loanId, @Param("chargeTimeValue") Integer chargeTimeValue);

}
