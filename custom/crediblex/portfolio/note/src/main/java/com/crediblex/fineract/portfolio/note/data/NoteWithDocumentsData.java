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
package com.crediblex.fineract.portfolio.note.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

/**
 * Extended NoteData that includes document attachments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Note information with document attachments")
public class NoteWithDocumentsData {

    @Schema(description = "Note ID", example = "1")
    private Long id;

    @Schema(description = "Client ID if this is a client note", example = "100")
    private Long clientId;

    @Schema(description = "Group ID if this is a group note", example = "50")
    private Long groupId;

    @Schema(description = "Loan ID if this is a loan note", example = "200")
    private Long loanId;

    @Schema(description = "Loan transaction ID if this is a loan transaction note", example = "300")
    private Long loanTransactionId;

    @Schema(description = "Savings account ID if this is a savings note", example = "400")
    private Long savingAccountId;

    @Schema(description = "Note type information")
    private EnumOptionData noteType;

    @Schema(description = "The note text content", example = "Meeting notes from client discussion")
    private String note;

    @Schema(description = "User ID who created the note", example = "1")
    private Long createdById;

    @Schema(description = "Username who created the note", example = "admin")
    private String createdByUsername;

    @Schema(description = "Timestamp when note was created")
    private OffsetDateTime createdOn;

    @Schema(description = "User ID who last updated the note", example = "1")
    private Long updatedById;

    @Schema(description = "Username who last updated the note", example = "admin")
    private String updatedByUsername;

    @Schema(description = "Timestamp when note was last updated")
    private OffsetDateTime updatedOn;

    @Schema(description = "List of documents attached to this note")
    private List<NoteDocumentData> documents;
}
