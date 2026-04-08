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
package com.crediblex.fineract.portfolio.note.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.portfolio.note.domain.Note;

/**
 * Entity representing the mapping between Notes and Documents. This allows multiple documents to be associated with a
 * single note.
 */
@Entity
@Table(name = "cx_note_document", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "note_id", "document_id" }, name = "uk_note_document") })
@Getter
@Setter
@NoArgsConstructor
public class NoteDocument extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "display_order")
    private Integer displayOrder;

    public NoteDocument(Note note, Document document, Integer displayOrder) {
        this.note = note;
        this.document = document;
        this.displayOrder = displayOrder;
    }

    public static NoteDocument create(Note note, Document document, Integer displayOrder) {
        return new NoteDocument(note, document, displayOrder);
    }
}
