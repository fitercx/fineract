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

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entry_odoo_sync")
@Data
@NoArgsConstructor
public class JournalEntryOdooSync extends AbstractPersistableCustom<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false, unique = true)
    private JournalEntry journalEntry;

    @Column(name = "is_posted_to_odoo", nullable = false)
    private Boolean isPostedToOdoo = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "odoo_move_id")
    private Long odooMoveId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isPostedToOdoo == null) {
            this.isPostedToOdoo = false;
        }
    }

    public JournalEntryOdooSync(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
        this.isPostedToOdoo = false;
    }

    public void markAsPosted(Long odooMoveId) {
        this.isPostedToOdoo = true;
        this.postedAt = LocalDateTime.now();
        this.odooMoveId = odooMoveId;
        this.errorMessage = null;
    }

    public void markAsFailed(String errorMessage) {
        this.isPostedToOdoo = false;
        this.errorMessage = errorMessage;
    }
}
