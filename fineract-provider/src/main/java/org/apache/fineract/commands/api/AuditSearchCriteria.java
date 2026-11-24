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
package org.apache.fineract.commands.api;

import java.time.LocalDate;
import java.util.Locale;
import org.apache.fineract.commands.data.request.AuditRequest;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;

/**
 * Builds the audit search WHERE clause. Maker and checker dates are calendar days of the timestamp shown on the screen,
 * which is {@code made_on_date_utc} when present and {@code made_on_date} otherwise. The same preference is used for
 * the checked date. From and To are inclusive of the whole selected day.
 */
final class AuditSearchCriteria {

    static final String MADE_ON_DAY = "CAST(COALESCE(aud.made_on_date_utc, aud.made_on_date) AS date)";

    static final String CHECKED_ON_DAY = "CAST(COALESCE(aud.checked_on_date_utc, aud.checked_on_date) AS date)";

    private AuditSearchCriteria() {}

    static SQLBuilder build(AuditRequest auditRequest) {
        SQLBuilder extraCriteria = new SQLBuilder();
        extraCriteria.addNonNullCriteria("aud.action_name = ", auditRequest.getActionName());
        if (auditRequest.getEntityName() != null) {
            extraCriteria.addCriteria("aud.entity_name like", auditRequest.getEntityName() + "%");
        }
        extraCriteria.addNonNullCriteria("aud.resource_id = ", auditRequest.getResourceId());
        extraCriteria.addNonNullCriteria("aud.maker_id = ", auditRequest.getMakerId());
        extraCriteria.addNonNullCriteria("aud.checker_id = ", auditRequest.getCheckerId());
        extraCriteria.addDateExpressionCriteria(MADE_ON_DAY, ">=", parseFilterDate(auditRequest.getMakerDateTimeFrom(), auditRequest));
        extraCriteria.addDateExpressionCriteria(MADE_ON_DAY, "<=", parseFilterDate(auditRequest.getMakerDateTimeTo(), auditRequest));
        extraCriteria.addDateExpressionCriteria(CHECKED_ON_DAY, ">=", parseFilterDate(auditRequest.getCheckerDateTimeFrom(), auditRequest));
        extraCriteria.addDateExpressionCriteria(CHECKED_ON_DAY, "<=", parseFilterDate(auditRequest.getCheckerDateTimeTo(), auditRequest));
        extraCriteria.addNonNullCriteria("aud.status = ", auditRequest.getStatus());
        extraCriteria.addNonNullCriteria("aud.office_id = ", auditRequest.getOfficeId());
        extraCriteria.addNonNullCriteria("aud.group_id = ", auditRequest.getGroupId());
        extraCriteria.addNonNullCriteria("aud.client_id = ", auditRequest.getClientId());
        extraCriteria.addNonNullCriteria("aud.loan_id = ", auditRequest.getLoanId());
        extraCriteria.addNonNullCriteria("aud.savings_account_id = ", auditRequest.getSavingsAccountId());
        return extraCriteria;
    }

    private static LocalDate parseFilterDate(String value, AuditRequest auditRequest) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        Locale locale = auditRequest.getLocale() == null || auditRequest.getLocale().isBlank() ? null
                : Locale.forLanguageTag(auditRequest.getLocale());
        if (auditRequest.getDateFormat() != null && !auditRequest.getDateFormat().isBlank()) {
            return DateUtils.parseLocalDate(trimmed, auditRequest.getDateFormat(), locale);
        }
        int timeSeparator = Math.max(trimmed.indexOf(' '), trimmed.indexOf('T'));
        if (timeSeparator > 0) {
            trimmed = trimmed.substring(0, timeSeparator);
        }
        return DateUtils.parseLocalDate(trimmed, "yyyy-MM-dd", locale);
    }
}
