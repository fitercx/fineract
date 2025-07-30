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

package com.crediblex.fineract.loc.repository;

import com.crediblex.fineract.loc.domain.LineOfCreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for Line of Credit Transaction entity.
 */
@Repository
public interface LineOfCreditTransactionRepository extends JpaRepository<LineOfCreditTransaction, Long> {

    /**
     * Find all transactions by line of credit ID.
     *
     * @param lineOfCreditId the line of credit ID
     * @return list of transactions
     */
    List<LineOfCreditTransaction> findByLineOfCreditIdOrderByTransactionDateDesc(Long lineOfCreditId);

    /**
     * Find all transactions by line of credit ID with pagination.
     *
     * @param lineOfCreditId the line of credit ID
     * @param pageable pagination parameters
     * @return page of transactions
     */
    Page<LineOfCreditTransaction> findByLineOfCreditIdOrderByTransactionDateDesc(Long lineOfCreditId, Pageable pageable);

    /**
     * Find transactions by line of credit ID and transaction type.
     *
     * @param lineOfCreditId the line of credit ID
     * @param transactionType the transaction type
     * @return list of transactions
     */
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionTypeOrderByTransactionDateDesc(
            Long lineOfCreditId, LineOfCreditTransaction.TransactionType transactionType);

    /**
     * Find transactions by line of credit ID within a date range.
     *
     * @param lineOfCreditId the line of credit ID
     * @param startDate the start date
     * @param endDate the end date
     * @return list of transactions
     */
    @Query("SELECT t FROM LineOfCreditTransaction t WHERE t.lineOfCredit.id = :lineOfCreditId " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionDateBetween(
            @Param("lineOfCreditId") Long lineOfCreditId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find transaction by reference number.
     *
     * @param referenceNumber the reference number
     * @return optional transaction
     */
    LineOfCreditTransaction findByReferenceNumber(String referenceNumber);
} 