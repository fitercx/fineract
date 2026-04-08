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

import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsData;
import java.util.List;

/**
 * Service interface for reading notes with their associated documents.
 */
public interface NoteWithDocumentsReadService {

    /**
     * Retrieve a single note with its documents.
     *
     * @param noteId
     *            the note ID
     * @param resourceId
     *            the resource ID (client/loan/etc.)
     * @param noteTypeId
     *            the note type
     * @return note data with documents
     */
    NoteWithDocumentsData retrieveNote(Long noteId, Long resourceId, Integer noteTypeId);

    /**
     * Retrieve all notes for a resource with their documents.
     *
     * @param resourceId
     *            the resource ID (client/loan/etc.)
     * @param noteTypeId
     *            the note type
     * @return list of notes with documents
     */
    List<NoteWithDocumentsData> retrieveNotesByResource(Long resourceId, Integer noteTypeId);
}
