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

import java.util.List;
import java.util.Optional;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Line of Credit entity.
 */
@Repository
public interface LineOfCreditRepository extends JpaRepository<LineOfCredit, Long> {

    @Query("""
        SELECT new com.crediblex.fineract.portfolio.loc.data.LineOfCreditSummary(
            l.id,
            l.externalId,
            l.productType
        )
        FROM LineOfCredit l
        WHERE l.status = com.crediblex.fineract.portfolio.loc.data.LocStatus.ACTIVE
          AND l.currency = :currency
        """)
    List<LineOfCreditSummary> findActiveSummariesByCurrency(@Param("currency") String currency);

    Optional<LineOfCredit> findBySettlementSavingsAccount_Id(Long savingsAccountId);
}
