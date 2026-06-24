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

import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.CommandNotFoundException;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformServiceImpl;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXPortfolioCommandSourceWritePlatformService extends PortfolioCommandSourceWritePlatformServiceImpl {

    private final PlatformSecurityContext platformSecurityContext;
    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;
    private final CommandSourceRepository commandSourceRepository;

    public CredXPortfolioCommandSourceWritePlatformService(final PlatformSecurityContext context,
            final CommandSourceRepository commandSourceRepository, final FromJsonHelper fromApiJsonHelper,
            final CommandProcessingService processAndLogCommandService, final SchedulerJobRunnerReadService schedulerJobRunnerReadService,
            final ConfigurationDomainService configurationService,
            final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService) {
        super(context, commandSourceRepository, fromApiJsonHelper, processAndLogCommandService, schedulerJobRunnerReadService,
                configurationService);
        this.platformSecurityContext = context;
        this.commandSourceRepository = commandSourceRepository;
        this.superUserAdministrationAuthorizationService = superUserAdministrationAuthorizationService;
    }

    @Override
    public CommandProcessingResult logCommandSource(final CommandWrapper wrapper) {
        final AppUser authenticatedUser = this.platformSecurityContext.authenticatedUser(wrapper);
        if (superUserAdministrationAuthorizationService.requiresSuperUserForCommand(wrapper, authenticatedUser.getId())) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(authenticatedUser);
        }
        return super.logCommandSource(wrapper);
    }

    @Override
    public CommandProcessingResult approveEntry(final Long makerCheckerId) {
        validateSuperUserForPendingCommand(makerCheckerId);
        return super.approveEntry(makerCheckerId);
    }

    @Override
    public Long deleteEntry(final Long makerCheckerId) {
        validateSuperUserForPendingCommand(makerCheckerId);
        return super.deleteEntry(makerCheckerId);
    }

    @Override
    public Long rejectEntry(final Long makerCheckerId) {
        validateSuperUserForPendingCommand(makerCheckerId);
        return super.rejectEntry(makerCheckerId);
    }

    private void validateSuperUserForPendingCommand(final Long makerCheckerId) {
        final CommandSource commandSource = this.commandSourceRepository.findById(makerCheckerId)
                .orElseThrow(() -> new CommandNotFoundException(makerCheckerId));
        if (superUserAdministrationAuthorizationService.requiresSuperUserForEntity(commandSource.getEntityName())) {
            superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
        }
    }

}
