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
package com.crediblex.fineract.portfolio.loanaccount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

/**
 * One row per loan that has ever been switched by the DPD strategy auto-switch, holding the strategy the loan used
 * before the switch so a revert can restore it verbatim. The row is kept after a revert so the history of the loan's
 * switches remains queryable and so a later re-switch can reuse it.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "m_loan_dpd_strategy_switch")
public class LoanDpdStrategySwitch extends AbstractPersistableCustom<Long> {

    @Column(name = "loan_id", nullable = false, unique = true)
    private Long loanId;

    @Column(name = "original_strategy_code", nullable = false, length = 100)
    private String originalStrategyCode;

    @Column(name = "original_strategy_name", length = 200)
    private String originalStrategyName;

    @Column(name = "switched_strategy_code", length = 100)
    private String switchedStrategyCode;

    @Column(name = "is_switched", nullable = false)
    private boolean switched;

    @Column(name = "max_dpd_at_switch")
    private Integer maxDpdAtSwitch;

    @Column(name = "switched_on_date")
    private LocalDate switchedOnDate;

    @Column(name = "reverted_on_date")
    private LocalDate revertedOnDate;

    @Column(name = "created_on_utc")
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;

    public static LoanDpdStrategySwitch newSwitch(final Long loanId, final String originalStrategyCode, final String originalStrategyName,
            final String switchedStrategyCode, final int maxDpd, final LocalDate switchedOn) {
        final LoanDpdStrategySwitch entity = new LoanDpdStrategySwitch();
        entity.loanId = loanId;
        entity.createdOnUtc = DateUtils.getAuditOffsetDateTime();
        entity.markSwitched(originalStrategyCode, originalStrategyName, switchedStrategyCode, maxDpd, switchedOn);
        return entity;
    }

    public void markSwitched(final String originalStrategyCode, final String originalStrategyName, final String switchedStrategyCode,
            final int maxDpd, final LocalDate switchedOn) {
        this.originalStrategyCode = originalStrategyCode;
        this.originalStrategyName = originalStrategyName;
        this.switchedStrategyCode = switchedStrategyCode;
        this.maxDpdAtSwitch = maxDpd;
        this.switchedOnDate = switchedOn;
        this.revertedOnDate = null;
        this.switched = true;
        this.lastModifiedOnUtc = DateUtils.getAuditOffsetDateTime();
    }

    public void markReverted(final LocalDate revertedOn) {
        this.switched = false;
        this.revertedOnDate = revertedOn;
        this.lastModifiedOnUtc = DateUtils.getAuditOffsetDateTime();
    }
}
