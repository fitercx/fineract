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

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.PermissionRepository;
import org.apache.fineract.useradministration.serialization.PermissionsCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.service.PermissionWritePlatformServiceJpaRepositoryImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXPermissionWritePlatformServiceJpaRepositoryImpl extends PermissionWritePlatformServiceJpaRepositoryImpl {

    private final PlatformSecurityContext platformSecurityContext;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;

    public CredXPermissionWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context,
            final PermissionRepository permissionRepository, final PermissionsCommandFromApiJsonDeserializer fromApiJsonDeserializer,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        super(context, permissionRepository, fromApiJsonDeserializer);
        this.platformSecurityContext = context;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public CommandProcessingResult updateMakerCheckerPermissions(final JsonCommand command) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.updateMakerCheckerPermissions(command);
    }

}
