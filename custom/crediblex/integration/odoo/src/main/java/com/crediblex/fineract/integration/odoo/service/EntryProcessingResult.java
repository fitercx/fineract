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
package com.crediblex.fineract.integration.odoo.service;

import java.util.List;
import java.util.Map;

/**
 * Result class to track individual journal entry processing outcomes
 * Provides detailed information about which specific entries succeeded or failed
 */
public class EntryProcessingResult {
    private final List<Long> successfulEntryIds;
    private final Map<Long, String> failedEntryIds;
    private final int movesCreated;
    private final Map<Integer, Long> journalToMoveMap;

    public EntryProcessingResult(List<Long> successfulEntryIds, Map<Long, String> failedEntryIds, 
                                int movesCreated, Map<Integer, Long> journalToMoveMap) {
        this.successfulEntryIds = successfulEntryIds;
        this.failedEntryIds = failedEntryIds;
        this.movesCreated = movesCreated;
        this.journalToMoveMap = journalToMoveMap;
    }

    public List<Long> getSuccessfulEntryIds() {
        return successfulEntryIds;
    }

    public Map<Long, String> getFailedEntryIds() {
        return failedEntryIds;
    }

    public int getSuccessCount() {
        return successfulEntryIds.size();
    }

    public int getFailureCount() {
        return failedEntryIds.size();
    }

    public int getMovesCreated() {
        return movesCreated;
    }

    public Map<Integer, Long> getJournalToMoveMap() {
        return journalToMoveMap;
    }

    public boolean hasAnySuccess() {
        return !successfulEntryIds.isEmpty();
    }

    public boolean hasAnyFailure() {
        return !failedEntryIds.isEmpty();
    }
}