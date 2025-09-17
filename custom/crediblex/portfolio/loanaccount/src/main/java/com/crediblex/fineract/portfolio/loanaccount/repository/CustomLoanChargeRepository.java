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

import jakarta.transaction.Transactional;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying
    @Transactional
    @Query("""
            UPDATE LoanCharge lc
            SET lc.active = false
            WHERE lc.loan.id = :loanId
            AND lc.id IN :chargeIds
            """)
    int deactivateCharges(@Param("loanId") Long loanId, @Param("chargeIds") List<Long> chargeIds);

}
