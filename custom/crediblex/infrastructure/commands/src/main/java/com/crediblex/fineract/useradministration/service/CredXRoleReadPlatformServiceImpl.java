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

import java.util.Collection;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.data.RoleData;
import org.apache.fineract.useradministration.service.RoleReadPlatformServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXRoleReadPlatformServiceImpl extends RoleReadPlatformServiceImpl {

    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;
    private final PlatformSecurityContext platformSecurityContext;

    public CredXRoleReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService,
            final PlatformSecurityContext platformSecurityContext) {
        super(jdbcTemplate);
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
        this.platformSecurityContext = platformSecurityContext;
    }

    @Override
    public Collection<RoleData> retrieveAll() {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.retrieveAll();
    }

    @Override
    public RoleData retrieveOne(final Long roleId) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.retrieveOne(roleId);
    }

}
