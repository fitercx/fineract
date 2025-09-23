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
package com.crediblex.fineract.integration.odoo.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryOdooSyncRepository extends JpaRepository<JournalEntryOdooSync, Long> {

    Optional<JournalEntryOdooSync> findByJournalEntryId(Long journalEntryId);

    @Query("SELECT j FROM JournalEntryOdooSync j WHERE j.isPostedToOdoo = false")
    List<JournalEntryOdooSync> findUnpostedEntries();

    @Query("SELECT j FROM JournalEntryOdooSync j WHERE j.isPostedToOdoo = false AND j.errorMessage IS NULL")
    List<JournalEntryOdooSync> findPendingEntries();

    @Query("SELECT j FROM JournalEntryOdooSync j WHERE j.isPostedToOdoo = false AND j.errorMessage IS NOT NULL")
    List<JournalEntryOdooSync> findFailedEntries();

    @Query("SELECT COUNT(j) FROM JournalEntryOdooSync j WHERE j.isPostedToOdoo = false")
    long countUnpostedEntries();

    @Query("SELECT j FROM JournalEntryOdooSync j WHERE j.loanId = :loanId AND j.isPostedToOdoo = false")
    List<JournalEntryOdooSync> findUnpostedEntriesByLoanId(@Param("loanId") Long loanId);

    @Query("SELECT DISTINCT j.loanId FROM JournalEntryOdooSync j WHERE j.isPostedToOdoo = false AND j.loanId IS NOT NULL")
    List<Long> findDistinctLoanIdsWithUnpostedEntries();

    @Query("SELECT j FROM JournalEntryOdooSync j WHERE j.loanId = :loanId")
    List<JournalEntryOdooSync> findByLoanId(@Param("loanId") Long loanId);
}
