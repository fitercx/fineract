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
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.service.AppUserReadPlatformServiceImpl;
import org.apache.fineract.useradministration.service.RoleReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXAppUserReadPlatformServiceImpl extends AppUserReadPlatformServiceImpl {

    private final PlatformSecurityContext platformSecurityContext;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;

    public CredXAppUserReadPlatformServiceImpl(final PlatformSecurityContext context, final JdbcTemplate jdbcTemplate,
            final OfficeReadPlatformService officeReadPlatformService, final RoleReadPlatformService roleReadPlatformService,
            final AppUserRepository appUserRepository, final StaffReadPlatformService staffReadPlatformService,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        super(context, jdbcTemplate, officeReadPlatformService, roleReadPlatformService, appUserRepository, staffReadPlatformService);
        this.platformSecurityContext = context;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public Collection<AppUserData> retrieveAllUsers() {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.retrieveAllUsers();
    }

    @Override
    public AppUserData retrieveUser(final Long userId) {
        final AppUser authenticatedUser = this.platformSecurityContext.authenticatedUser();
        if (!authenticatedUser.hasIdOf(userId)) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(authenticatedUser);
        }
        return super.retrieveUser(userId);
    }

    @Override
    public AppUserData retrieveNewUserDetails() {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.retrieveNewUserDetails();
    }

}
