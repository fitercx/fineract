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
package com.crediblex.fineract.portfolio.note.service;

import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsRequest;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

/**
 * Service interface for writing notes with their associated documents.
 */
public interface NoteWithDocumentsWriteService {

    /**
     * Create a note with optional document attachments.
     *
     * @param resourceType
     *            the resource type (clients, loans, etc.)
     * @param resourceId
     *            the resource ID
     * @param request
     *            the note request with documents
     * @return command processing result with created note ID
     */
    CommandProcessingResult createNoteWithDocuments(String resourceType, Long resourceId, NoteWithDocumentsRequest request);

    /**
     * Update a note and its document attachments.
     *
     * @param resourceType
     *            the resource type (clients, loans, etc.)
     * @param resourceId
     *            the resource ID
     * @param noteId
     *            the note ID to update
     * @param request
     *            the note request with documents
     * @return command processing result
     */
    CommandProcessingResult updateNoteWithDocuments(String resourceType, Long resourceId, Long noteId, NoteWithDocumentsRequest request);

    /**
     * Delete a note and its document associations.
     *
     * @param resourceType
     *            the resource type (clients, loans, etc.)
     * @param resourceId
     *            the resource ID
     * @param noteId
     *            the note ID to delete
     * @return command processing result
     */
    CommandProcessingResult deleteNoteWithDocuments(String resourceType, Long resourceId, Long noteId);
}
