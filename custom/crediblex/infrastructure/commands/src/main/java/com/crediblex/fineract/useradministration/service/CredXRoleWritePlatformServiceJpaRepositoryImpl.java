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
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.serialization.PermissionsCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.service.RoleDataValidator;
import org.apache.fineract.useradministration.service.RoleWritePlatformServiceJpaRepositoryImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXRoleWritePlatformServiceJpaRepositoryImpl extends RoleWritePlatformServiceJpaRepositoryImpl {

    private final PlatformSecurityContext platformSecurityContext;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;

    public CredXRoleWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context, final RoleRepository roleRepository,
            final PermissionRepository permissionRepository, final RoleDataValidator roleCommandFromApiJsonDeserializer,
            final PermissionsCommandFromApiJsonDeserializer permissionsFromApiJsonDeserializer,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        super(context, roleRepository, permissionRepository, roleCommandFromApiJsonDeserializer, permissionsFromApiJsonDeserializer);
        this.platformSecurityContext = context;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public CommandProcessingResult createRole(final JsonCommand command) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.createRole(command);
    }

    @Override
    public CommandProcessingResult updateRole(final Long roleId, final JsonCommand command) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        superUserAdministrationAuthorizationService.validateSuperUserRoleIsNotModified(roleId);
        return super.updateRole(roleId, command);
    }

    @Override
    public CommandProcessingResult updateRolePermissions(final Long roleId, final JsonCommand command) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        superUserAdministrationAuthorizationService.validateSuperUserRoleIsNotModified(roleId);
        return super.updateRolePermissions(roleId, command);
    }

    @Override
    public CommandProcessingResult deleteRole(final Long roleId) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        superUserAdministrationAuthorizationService.validateSuperUserRoleIsNotModified(roleId);
        return super.deleteRole(roleId);
    }

    @Override
    public CommandProcessingResult disableRole(final Long roleId) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        superUserAdministrationAuthorizationService.validateSuperUserRoleIsNotModified(roleId);
        return super.disableRole(roleId);
    }

    @Override
    public CommandProcessingResult enableRole(final Long roleId) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        superUserAdministrationAuthorizationService.validateSuperUserRoleIsNotModified(roleId);
        return super.enableRole(roleId);
    }

}
