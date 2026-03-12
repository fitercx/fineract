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
package com.crediblex.fineract.portfolio.note.api;

import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsData;
import com.crediblex.fineract.portfolio.note.data.NoteWithDocumentsRequest;
import com.crediblex.fineract.portfolio.note.service.NoteWithDocumentsReadService;
import com.crediblex.fineract.portfolio.note.service.NoteWithDocumentsWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.note.domain.NoteType;
import org.apache.fineract.portfolio.note.exception.NoteResourceNotSupportedException;
import org.springframework.stereotype.Component;

/**
 * Extended Notes API that supports document attachments. This API provides endpoints for creating, reading, updating,
 * and deleting notes with associated documents.
 */
@Path("/v1/{resourceType}/{resourceId}/notes-with-documents")
@Component
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notes with Documents", description = "Extended Notes API that supports document attachments")
public class NotesWithDocumentsApiResource {

    private static final String CLIENTNOTE = "CLIENTNOTE";
    private static final String LOANNOTE = "LOANNOTE";

    private final PlatformSecurityContext securityContext;
    private final NoteWithDocumentsReadService readService;
    private final NoteWithDocumentsWriteService writeService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve all notes with documents for a resource", description = "Retrieves all notes with their associated documents for a client, loan, or other supported resource. "
            + "Notes are returned in descending creation order.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Notes retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Resource not found") })
    public List<NoteWithDocumentsData> retrieveNotesByResource(
            @PathParam("resourceType") @Parameter(description = "Resource type: clients, loans, groups, etc.") final String resourceType,
            @PathParam("resourceId") @Parameter(description = "Resource ID") final Long resourceId) {

        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        securityContext.authenticatedUser().validateHasReadPermission(getEntityName(noteType));

        return readService.retrieveNotesByResource(resourceId, noteType.getValue());
    }

    @GET
    @Path("{noteId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a single note with documents", description = "Retrieves a specific note with its associated documents")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Note retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Note not found") })
    public NoteWithDocumentsData retrieveNote(
            @PathParam("resourceType") @Parameter(description = "Resource type: clients, loans, groups, etc.") final String resourceType,
            @PathParam("resourceId") @Parameter(description = "Resource ID") final Long resourceId,
            @PathParam("noteId") @Parameter(description = "Note ID") final Long noteId) {

        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        securityContext.authenticatedUser().validateHasReadPermission(getEntityName(noteType));

        return readService.retrieveNote(noteId, resourceId, noteType.getValue());
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a note with document attachments", description = "Creates a new note with optional document attachments. Documents should already be uploaded to S3 "
            + "using the presigned URL endpoint before calling this API.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note created successfully", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request") })
    public CommandProcessingResult createNote(
            @PathParam("resourceType") @Parameter(description = "Resource type: clients, loans, groups, etc.") final String resourceType,
            @PathParam("resourceId") @Parameter(description = "Resource ID") final Long resourceId,
            @Parameter(description = "Note with documents request", required = true) final NoteWithDocumentsRequest request) {

        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        securityContext.authenticatedUser().validateHasCreatePermission(getEntityName(noteType));

        return writeService.createNoteWithDocuments(resourceType, resourceId, request);
    }

    @PUT
    @Path("{noteId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a note and its document attachments", description = "Updates an existing note and replaces its document attachments")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note updated successfully", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))),
            @ApiResponse(responseCode = "404", description = "Note not found") })
    public CommandProcessingResult updateNote(
            @PathParam("resourceType") @Parameter(description = "Resource type: clients, loans, groups, etc.") final String resourceType,
            @PathParam("resourceId") @Parameter(description = "Resource ID") final Long resourceId,
            @PathParam("noteId") @Parameter(description = "Note ID") final Long noteId,
            @Parameter(description = "Note with documents request", required = true) final NoteWithDocumentsRequest request) {

        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        securityContext.authenticatedUser().validateHasUpdatePermission(getEntityName(noteType));

        return writeService.updateNoteWithDocuments(resourceType, resourceId, noteId, request);
    }

    @DELETE
    @Path("{noteId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a note and its document associations", description = "Deletes a note and removes document associations. Note: The actual files remain in S3.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note deleted successfully", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))),
            @ApiResponse(responseCode = "404", description = "Note not found") })
    public CommandProcessingResult deleteNote(
            @PathParam("resourceType") @Parameter(description = "Resource type: clients, loans, groups, etc.") final String resourceType,
            @PathParam("resourceId") @Parameter(description = "Resource ID") final Long resourceId,
            @PathParam("noteId") @Parameter(description = "Note ID") final Long noteId) {

        final NoteType noteType = NoteType.fromApiUrl(resourceType);
        if (noteType == null) {
            throw new NoteResourceNotSupportedException(resourceType);
        }

        securityContext.authenticatedUser().validateHasDeletePermission(getEntityName(noteType));

        return writeService.deleteNoteWithDocuments(resourceType, resourceId, noteId);
    }

    private String getEntityName(NoteType noteType) {
        return switch (noteType) {
            case CLIENT -> CLIENTNOTE;
            case LOAN -> LOANNOTE;
            default -> noteType.name() + "NOTE";
        };
    }
}
