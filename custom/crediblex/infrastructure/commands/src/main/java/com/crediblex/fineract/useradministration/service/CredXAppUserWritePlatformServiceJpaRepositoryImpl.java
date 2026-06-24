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
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserPreviousPasswordRepository;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.apache.fineract.useradministration.service.AppUserWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.useradministration.service.UserDataValidator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXAppUserWritePlatformServiceJpaRepositoryImpl extends AppUserWritePlatformServiceJpaRepositoryImpl {

    private final PlatformSecurityContext platformSecurityContext;
    private final AppUserRepository appUserRepository;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;

    public CredXAppUserWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context,
            final UserDomainService userDomainService, final PlatformPasswordEncoder platformPasswordEncoder,
            final AppUserRepository appUserRepository, final OfficeRepositoryWrapper officeRepositoryWrapper,
            final RoleRepository roleRepository, final UserDataValidator fromApiJsonDeserializer,
            final AppUserPreviousPasswordRepository appUserPreviewPasswordRepository, final StaffRepositoryWrapper staffRepositoryWrapper,
            final ClientRepositoryWrapper clientRepositoryWrapper,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        super(context, userDomainService, platformPasswordEncoder, appUserRepository, officeRepositoryWrapper, roleRepository,
                fromApiJsonDeserializer, appUserPreviewPasswordRepository, staffRepositoryWrapper, clientRepositoryWrapper);
        this.platformSecurityContext = context;
        this.appUserRepository = appUserRepository;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public CommandProcessingResult createUser(final JsonCommand command) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        return super.createUser(command);
    }

    @Override
    public CommandProcessingResult updateUser(final Long userId, final JsonCommand command) {
        final AppUser authenticatedUser = this.platformSecurityContext.authenticatedUser();
        if (!authenticatedUser.hasIdOf(userId)) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(authenticatedUser);
        }
        return super.updateUser(userId, command);
    }

    @Override
    public CommandProcessingResult deleteUser(final Long userId) {
        superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        final AppUser user = this.appUserRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        superUserAdministrationAuthorizationService.validateSuperUserAccountIsNotDeleted(user);
        return super.deleteUser(userId);
    }

}
