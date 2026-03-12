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

import com.crediblex.fineract.infrastructure.s3.config.S3Config;
import com.crediblex.fineract.portfolio.note.data.NoteDocumentData;
import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsData;
import com.crediblex.fineract.portfolio.note.domain.NoteDocument;
import com.crediblex.fineract.portfolio.note.domain.NoteDocumentRepository;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.portfolio.note.data.NoteData;
import org.apache.fineract.portfolio.note.service.NoteReadPlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NoteWithDocumentsReadServiceImpl implements NoteWithDocumentsReadService {

    private final NoteReadPlatformService noteReadPlatformService;
    private final NoteDocumentRepository noteDocumentRepository;
    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    @Override
    public NoteWithDocumentsData retrieveNote(Long noteId, Long resourceId, Integer noteTypeId) {
        NoteData noteData = noteReadPlatformService.retrieveNote(noteId, resourceId, noteTypeId);
        List<NoteDocumentData> documents = getDocumentsForNote(noteId);
        return mapToNoteWithDocuments(noteData, documents);
    }

    @Override
    public List<NoteWithDocumentsData> retrieveNotesByResource(Long resourceId, Integer noteTypeId) {
        List<NoteData> notes = noteReadPlatformService.retrieveNotesByResource(resourceId, noteTypeId);

        if (notes == null || notes.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all note IDs
        List<Long> noteIds = notes.stream().map(NoteData::getId).collect(Collectors.toList());

        // Fetch all documents for these notes in one query
        List<NoteDocument> noteDocuments = noteDocumentRepository.findByNoteIdInOrderByDisplayOrderAsc(noteIds);

        // Group documents by note ID
        Map<Long, List<NoteDocumentData>> documentsByNoteId = noteDocuments.stream().collect(
                Collectors.groupingBy(nd -> nd.getNote().getId(), Collectors.mapping(this::mapToNoteDocumentData, Collectors.toList())));

        // Map each note with its documents
        return notes.stream()
                .map(note -> mapToNoteWithDocuments(note, documentsByNoteId.getOrDefault(note.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private List<NoteDocumentData> getDocumentsForNote(Long noteId) {
        List<NoteDocument> noteDocuments = noteDocumentRepository.findByNoteIdOrderByDisplayOrderAsc(noteId);
        return noteDocuments.stream().map(this::mapToNoteDocumentData).collect(Collectors.toList());
    }

    private NoteDocumentData mapToNoteDocumentData(NoteDocument noteDocument) {
        Document document = noteDocument.getDocument();
        String location = document.getLocation();

        // Generate presigned GET URL if location is an S3 key
        String presignedUrl = null;
        Long presignedUrlExpiresInSeconds = null;

        if (location != null && !location.isEmpty()) {
            try {
                presignedUrl = generatePresignedGetUrl(location);
                presignedUrlExpiresInSeconds = s3Config.getPresignedUrlExpirationMinutes() * 60;
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL for document {}: {}", document.getId(), e.getMessage());
            }
        }

        return NoteDocumentData.builder().id(document.getId()).name(document.getName()).fileName(document.getFileName())
                .size(document.getSize()).type(document.getType()).description(document.getDescription()).location(location)
                .displayOrder(noteDocument.getDisplayOrder()).presignedUrl(presignedUrl)
                .presignedUrlExpiresInSeconds(presignedUrlExpiresInSeconds).build();
    }

    private String generatePresignedGetUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(s3Config.getBucketName()).key(objectKey).build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Config.getPresignedUrlExpirationMinutes())).getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    private NoteWithDocumentsData mapToNoteWithDocuments(NoteData noteData, List<NoteDocumentData> documents) {
        return NoteWithDocumentsData.builder().id(noteData.getId()).clientId(noteData.getClientId()).groupId(noteData.getGroupId())
                .loanId(noteData.getLoanId()).loanTransactionId(noteData.getLoanTransactionId())
                .savingAccountId(noteData.getSavingAccountId()).noteType(noteData.getNoteType()).note(noteData.getNote())
                .createdById(noteData.getCreatedById()).createdByUsername(noteData.getCreatedByUsername())
                .createdOn(noteData.getCreatedOn()).updatedById(noteData.getUpdatedById())
                .updatedByUsername(noteData.getUpdatedByUsername()).updatedOn(noteData.getUpdatedOn()).documents(documents).build();
    }
}
