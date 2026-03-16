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

import com.crediblex.fineract.portfolio.note.data.NoteDocumentRequest;
import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsRequest;
import com.crediblex.fineract.portfolio.note.domain.NoteDocument;
import com.crediblex.fineract.portfolio.note.domain.NoteDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.note.domain.NoteType;
import org.apache.fineract.portfolio.note.exception.NoteNotFoundException;
import org.apache.fineract.portfolio.note.exception.NoteResourceNotSupportedException;
import org.apache.fineract.portfolio.note.service.NoteReadPlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteWithDocumentsWriteServiceImpl implements NoteWithDocumentsWriteService {

    private static final String NOTES_ENTITY_TYPE = "NOTES";

    private final NoteRepository noteRepository;
    private final NoteDocumentRepository noteDocumentRepository;
    private final DocumentRepository documentRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final LoanRepositoryWrapper loanRepository;
    private final NoteReadPlatformService noteReadPlatformService;

    @Override
    @Transactional
    public CommandProcessingResult createNoteWithDocuments(String resourceType, Long resourceId, NoteWithDocumentsRequest request) {
        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        // Create the note first
        Note note = createNote(noteType, resourceId, request.getNote());
        noteRepository.saveAndFlush(note); // Need flush to get the note ID for document association

        // Create documents and associations
        if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
            createDocumentsForNote(note, request.getDocuments());
        }

        log.info("Created note with ID {} and {} documents for {} {}", note.getId(),
                request.getDocuments() != null ? request.getDocuments().size() : 0, resourceType, resourceId);

        return buildCommandProcessingResult(note, noteType, resourceId);
    }

    @Override
    @Transactional
    public CommandProcessingResult updateNoteWithDocuments(String resourceType, Long resourceId, Long noteId,
            NoteWithDocumentsRequest request) {
        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        Note note = noteRepository.findById(noteId).orElseThrow(() -> new NoteNotFoundException(noteId));

        // Validate that the note belongs to the specified resource
        validateNoteOwnership(note, noteType, resourceId);

        // Remove existing document associations and create new ones
        noteDocumentRepository.deleteByNote_Id(noteId);

        if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
            createDocumentsForNote(note, request.getDocuments());
        }

        log.info("Updated note with ID {} and {} documents for {} {}", note.getId(),
                request.getDocuments() != null ? request.getDocuments().size() : 0, resourceType, resourceId);

        return buildCommandProcessingResult(note, noteType, resourceId);
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteNoteWithDocuments(String resourceType, Long resourceId, Long noteId) {
        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        Note note = noteRepository.findById(noteId).orElseThrow(() -> new NoteNotFoundException(noteId));

        // Validate that the note belongs to the specified resource
        validateNoteOwnership(note, noteType, resourceId);

        // Delete document associations (not the actual documents - they stay in S3)
        noteDocumentRepository.deleteByNote_Id(noteId);

        // Delete the note
        noteRepository.delete(note);

        log.info("Deleted note with ID {} for {} {}", noteId, resourceType, resourceId);

        return new CommandProcessingResultBuilder().withEntityId(noteId).build();
    }

    private void validateNoteOwnership(Note note, NoteType noteType, Long resourceId) {
        // Use the read service to verify note exists for this resource
        // This will throw NoteNotFoundException if note doesn't belong to the resource
        noteReadPlatformService.retrieveNote(note.getId(), resourceId, noteType.getValue());
    }

    private Note createNote(NoteType noteType, Long resourceId, String noteText) {
        switch (noteType) {
            case CLIENT:
                Client client = clientRepository.findOneWithNotFoundDetection(resourceId);
                return new Note(client, noteText);
            case LOAN:
                Loan loan = loanRepository.findOneWithNotFoundDetection(resourceId);
                return Note.loanNote(loan, noteText);
            default:
                throw new NoteResourceNotSupportedException(noteType.getApiUrl());
        }
    }

    private void createDocumentsForNote(Note note, List<NoteDocumentRequest> documentRequests) {
        List<Document> documents = new ArrayList<>();
        List<NoteDocument> noteDocuments = new ArrayList<>();

        int displayOrder = 0;
        for (NoteDocumentRequest docRequest : documentRequests) {
            // Create Document entity for S3 storage
            Document document = Document.createNew(NOTES_ENTITY_TYPE, note.getId(),
                    StringUtils.defaultIfBlank(docRequest.getName(), docRequest.getFileName()), docRequest.getFileName(),
                    docRequest.getSize(), docRequest.getContentType(), docRequest.getDescription(), docRequest.getS3ObjectKey(),
                    StorageType.S3);
            documents.add(document);
        }

        // Batch save all documents
        documentRepository.saveAll(documents);

        // Create note-document associations
        for (Document document : documents) {
            NoteDocument noteDocument = NoteDocument.create(note, document, displayOrder++);
            noteDocuments.add(noteDocument);
            log.debug("Created document {} for note {} with S3 key {}", document.getId(), note.getId(), document.getLocation());
        }

        // Batch save all note-document associations
        noteDocumentRepository.saveAll(noteDocuments);
    }

    private CommandProcessingResult buildCommandProcessingResult(Note note, NoteType noteType, Long resourceId) {
        CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(note.getId());

        switch (noteType) {
            case CLIENT:
                Client client = clientRepository.findOneWithNotFoundDetection(resourceId);
                builder.withClientId(resourceId).withOfficeId(client.officeId());
            break;
            case LOAN:
                Loan loan = loanRepository.findOneWithNotFoundDetection(resourceId);
                builder.withLoanId(resourceId).withOfficeId(loan.getOfficeId());
            break;
            default:
            break;
        }

        return builder.build();
    }
}
