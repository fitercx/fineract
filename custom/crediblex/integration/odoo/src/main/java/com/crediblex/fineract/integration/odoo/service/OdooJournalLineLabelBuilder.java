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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds Odoo account.move.line {@code name} labels for loan-related journal posts.
 * <p>
 * Format: {@code [Client Name] - [Event] [Date] - [Product type] - Loan ID [id]}
 * Missing segments are omitted. Returns {@code null} for unsupported business events so callers
 * can keep their existing label behavior.
 */
public final class OdooJournalLineLabelBuilder {

    private static final Set<String> SUPPORTED_EVENTS = Set.of("DISBURSEMENT", "REPAYMENT", "ACCRUAL", "EARLY_CLOSURE");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private OdooJournalLineLabelBuilder() {}

    /**
     * @return structured line label, or {@code null} when the business event is not supported
     */
    public static String build(String clientName, String businessEventType, LocalDate transactionDate, String productShortName,
            Long loanId) {
        if (businessEventType == null || !SUPPORTED_EVENTS.contains(businessEventType)) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        if (isPresent(clientName)) {
            parts.add(clientName.trim());
        }

        String eventLabel = toEventLabel(businessEventType);
        if (transactionDate != null) {
            parts.add(eventLabel + " " + DATE_FORMAT.format(transactionDate));
        } else {
            parts.add(eventLabel);
        }

        String productLabel = toProductLabel(productShortName);
        if (productLabel != null) {
            parts.add(productLabel);
        }

        if (loanId != null) {
            parts.add("Loan ID " + loanId);
        }

        return String.join(" - ", parts);
    }

    private static String toEventLabel(String businessEventType) {
        return switch (businessEventType) {
            case "DISBURSEMENT" -> "Disbursement";
            case "REPAYMENT" -> "Repayment";
            case "ACCRUAL" -> "Accrual";
            case "EARLY_CLOSURE" -> "Early Closure";
            default -> businessEventType;
        };
    }

    private static String toProductLabel(String productShortName) {
        if (productShortName == null) {
            return null;
        }
        return switch (productShortName) {
            case "RF" -> "Invoice discounting";
            case "PF" -> "Payable Financing";
            case "RBF" -> "Revenue Based Financing";
            default -> null;
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
