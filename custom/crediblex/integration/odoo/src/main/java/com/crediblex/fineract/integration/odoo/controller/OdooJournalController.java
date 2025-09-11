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
package com.crediblex.fineract.integration.odoo.controller;

import com.crediblex.fineract.integration.odoo.service.OdooJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * REST API controller for Odoo integration
 */
@Path("/v1/odoo")
@Component
@Tag(name = "Odoo Integration", description = "Odoo ERP integration for journal entries")
@Slf4j
public class OdooJournalController {

    private final OdooJournalService odooJournalService;
    private final PlatformSecurityContext context;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @Autowired
    public OdooJournalController(OdooJournalService odooJournalService, PlatformSecurityContext context,
            DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer, ApiRequestParameterHelper apiRequestParameterHelper) {
        this.odooJournalService = odooJournalService;
        this.context = context;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
    }

//    @PostConstruct
//    public void init() {
//        log.info("OdooJournalController initialized");
//    }

    /**
     * Test Odoo connection
     */
    @GET
    @Path("/test")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Test Odoo Connection", description = "Tests the connection to Odoo ERP system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Map.class))) })
    public String testConnection(@Context final UriInfo uriInfo) {
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        try {
            boolean connected = odooJournalService.testConnection();
            Map<String, Object> result = Map.of("connected", connected, "message",
                    connected ? "Connection successful" : "Connection failed");
            return this.toApiJsonSerializer.serialize(settings, result, null);
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage(), e);
            Map<String, Object> result = Map.of("connected", false, "message", "Connection failed: " + e.getMessage());
            return this.toApiJsonSerializer.serialize(settings, result, null);
        }
    }

    /**
     * Post journal entry to Odoo
     */
    @POST
    @Path("/journal-entry")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Post Journal Entry to Odoo", description = "Creates a journal entry in Odoo ERP system.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = JournalEntryRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Map.class))) })
    public String postJournalEntry(@Parameter(hidden = true) final JournalEntryRequest request, @Context final UriInfo uriInfo) {
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        try {
            Map<String, Object> result = odooJournalService.postJournalEntry(request.getReference(), request.getDate(),
                    request.getDescription(), request.getAmount(), request.getDebitAccount(), request.getCreditAccount());
            return this.toApiJsonSerializer.serialize(settings, result, null);
        } catch (Exception e) {
            log.error("Journal entry posting failed: {}", e.getMessage(), e);
            Map<String, Object> result = Map.of("success", false, "error", e.getMessage());
            return this.toApiJsonSerializer.serialize(settings, result, null);
        }
    }

    /**
     * Journal entry request DTO
     */
    public static class JournalEntryRequest {

        private String reference;
        private LocalDate date;
        private String description;
        private BigDecimal amount;
        private String debitAccount;
        private String creditAccount;

        // Getters and setters
        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getDebitAccount() {
            return debitAccount;
        }

        public void setDebitAccount(String debitAccount) {
            this.debitAccount = debitAccount;
        }

        public String getCreditAccount() {
            return creditAccount;
        }

        public void setCreditAccount(String creditAccount) {
            this.creditAccount = creditAccount;
        }
    }
}
