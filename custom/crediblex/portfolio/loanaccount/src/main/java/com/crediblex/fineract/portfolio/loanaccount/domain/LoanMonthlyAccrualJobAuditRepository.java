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

import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanMonthlyAccrualJobAuditRepository extends JpaRepository<LoanMonthlyAccrualJobAudit, Long> {

    /**
     * Find all loan monthly accrual audit records that have not been posted to Odoo yet. These records need journal
     * entries to be created and posted.
     *
     * <p>
     * Accruals of Receivable Non-Recourse LOC loans are excluded (not posted to Odoo yet). They stay
     * {@code postedToOdoo = false} so a future backfill can pick them up.
     * </p>
     *
     * @return List of unposted accrual audit records
     */
    default List<LoanMonthlyAccrualJobAudit> findUnpostedAccruals() {
        return findUnpostedAccrualsExcludingLocProductType(LocProductType.RECEIVABLE_NON_RECOURSE);
    }

    @Query("""
            SELECT lmaja FROM LoanMonthlyAccrualJobAudit lmaja
            WHERE lmaja.postedToOdoo = false
              AND NOT EXISTS (
                  SELECT params.id FROM LoanLineOfCreditParams params
                  WHERE params.loan.id = lmaja.loanId
                    AND params.lineOfCredit.productType = :excludedProductType
              )
            ORDER BY lmaja.generatedOnDate ASC
            """)
    List<LoanMonthlyAccrualJobAudit> findUnpostedAccrualsExcludingLocProductType(
            @Param("excludedProductType") LocProductType excludedProductType);
}
