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

package com.crediblex.fineract.portfolio.loc.repository;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Line of Credit Transaction entity.
 */
@Repository
public interface LineOfCreditTransactionRepository extends JpaRepository<LineOfCreditTransaction, Long> {

    /**
     * Find all transactions for a specific line of credit.
     *
     * @param lineOfCreditId the line of credit ID
     * @return list of transactions
     */
    List<LineOfCreditTransaction> findByLineOfCreditIdOrderByTransactionDateDesc(Long lineOfCreditId);

    /**
     * Find all transactions for a specific line of credit by transaction type.
     *
     * @param lineOfCreditId the line of credit ID
     * @param transactionType the transaction type
     * @return list of transactions
     */
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionTypeOrderByTransactionDateDesc(Long lineOfCreditId, String transactionType);
}
