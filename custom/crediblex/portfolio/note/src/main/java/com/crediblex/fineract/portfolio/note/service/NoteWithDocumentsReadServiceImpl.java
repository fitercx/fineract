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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.portfolio.note.data.NoteData;
import org.apache.fineract.portfolio.note.service.NoteReadPlatformService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@Slf4j
@Transactional(readOnly = true)
public class NoteWithDocumentsReadServiceImpl implements NoteWithDocumentsReadService {

    private final NoteReadPlatformService noteReadPlatformService;
    private final NoteDocumentRepository noteDocumentRepository;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;
    private final ObjectProvider<S3Config> s3ConfigProvider;

    public NoteWithDocumentsReadServiceImpl(NoteReadPlatformService noteReadPlatformService, NoteDocumentRepository noteDocumentRepository,
            ObjectProvider<S3Presigner> s3PresignerProvider, ObjectProvider<S3Config> s3ConfigProvider) {
        this.noteReadPlatformService = noteReadPlatformService;
        this.noteDocumentRepository = noteDocumentRepository;
        this.s3PresignerProvider = s3PresignerProvider;
        this.s3ConfigProvider = s3ConfigProvider;
    }

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

        // Map each note with its documents and sort by createdOn descending (newest first)
        return notes.stream()
                .map(note -> mapToNoteWithDocuments(note, documentsByNoteId.getOrDefault(note.getId(), Collections.emptyList())))
                .sorted(Comparator.comparing(NoteWithDocumentsData::getCreatedOn, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private List<NoteDocumentData> getDocumentsForNote(Long noteId) {
        List<NoteDocument> noteDocuments = noteDocumentRepository.findByNote_IdOrderByDisplayOrderAsc(noteId);
        return noteDocuments.stream().map(this::mapToNoteDocumentData).collect(Collectors.toList());
    }

    private NoteDocumentData mapToNoteDocumentData(NoteDocument noteDocument) {
        Document document = noteDocument.getDocument();
        String location = document.getLocation();

        // Generate presigned GET URL only for S3 storage type
        String presignedUrl = null;
        Long presignedUrlExpiresInSeconds = null;

        S3Presigner s3Presigner = s3PresignerProvider.getIfAvailable();
        S3Config s3Config = s3ConfigProvider.getIfAvailable();

        // Only generate presigned URL if S3 is configured and document is stored in S3
        if (document.storageType() == StorageType.S3 && s3Presigner != null && s3Config != null && location != null
                && !location.isEmpty()) {
            try {
                presignedUrl = generatePresignedGetUrl(s3Presigner, s3Config, location);
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

    private String generatePresignedGetUrl(S3Presigner s3Presigner, S3Config s3Config, String objectKey) {
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
