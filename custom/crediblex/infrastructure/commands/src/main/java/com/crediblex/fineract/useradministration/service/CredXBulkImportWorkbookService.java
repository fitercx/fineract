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
package com.crediblex.fineract.useradministration.service;

import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Collection;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.infrastructure.bulkimport.data.ImportData;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookService;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookServiceImpl;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXBulkImportWorkbookService implements BulkImportWorkbookService {

    private final BulkImportWorkbookServiceImpl delegate;
    private final PlatformSecurityContext platformSecurityContext;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;

    public CredXBulkImportWorkbookService(final BulkImportWorkbookServiceImpl delegate,
            final PlatformSecurityContext platformSecurityContext,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        this.delegate = delegate;
        this.platformSecurityContext = platformSecurityContext;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public Long importWorkbook(final String entityType, final InputStream inputStream, final FormDataContentDisposition fileDetail,
            final String locale, final String dateFormat) {
        validateSuperUserForUsersImport(entityType);
        return this.delegate.importWorkbook(entityType, inputStream, fileDetail, locale, dateFormat);
    }

    @Override
    public Collection<ImportData> getImports(final GlobalEntityType type) {
        if (GlobalEntityType.USERS.equals(type)) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        }
        return this.delegate.getImports(type);
    }

    @Override
    public DocumentData getOutputTemplateLocation(final String importDocumentId) {
        return this.delegate.getOutputTemplateLocation(importDocumentId);
    }

    @Override
    public Response getOutputTemplate(final String importDocumentId) {
        return this.delegate.getOutputTemplate(importDocumentId);
    }

    private void validateSuperUserForUsersImport(final String entityType) {
        if (isUsersEntityType(entityType)) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        }
    }

    private boolean isUsersEntityType(final String entityType) {
        return entityType != null && entityType.trim().equalsIgnoreCase(GlobalEntityType.USERS.toString());
    }

}
