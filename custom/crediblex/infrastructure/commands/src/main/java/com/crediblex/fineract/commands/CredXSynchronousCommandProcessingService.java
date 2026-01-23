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
package com.crediblex.fineract.commands;

import java.time.Instant;
import java.util.Map;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.provider.CommandHandlerProvider;
import org.apache.fineract.commands.service.CommandSourceService;
import org.apache.fineract.commands.service.IdempotencyKeyResolver;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.hooks.event.HookEvent;
import org.apache.fineract.infrastructure.hooks.event.HookEventSource;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class CredXSynchronousCommandProcessingService extends SynchronousCommandProcessingService {

    public CredXSynchronousCommandProcessingService(PlatformSecurityContext context, ApplicationContext applicationContext,
            ToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer,
            ToApiJsonSerializer<CommandProcessingResult> toApiResultJsonSerializer, ConfigurationDomainService configurationDomainService,
            CommandHandlerProvider commandHandlerProvider, IdempotencyKeyResolver idempotencyKeyResolver,
            CommandSourceService commandSourceService, FineractRequestContextHolder fineractRequestContextHolder) {
        super(context, applicationContext, toApiJsonSerializer, toApiResultJsonSerializer, configurationDomainService,
                commandHandlerProvider, idempotencyKeyResolver, commandSourceService, fineractRequestContextHolder);
    }

    @Override
    public void publishHookEvent(final String entityName, final String actionName, JsonCommand command, final Object result) {
        super.publishHookEvent(entityName, actionName, command, result);
    }

    public void publishHookEventRaw(final String entityName, final String actionName, final Map<String, Object> reqmap) {

        final AppUser appUser = context.authenticatedUser(CommandWrapper.wrap(actionName, entityName, null, null));
        final HookEventSource hookEventSource = new HookEventSource(entityName, actionName);

        reqmap.putIfAbsent("entityName", entityName);
        reqmap.putIfAbsent("actionName", actionName);
        reqmap.putIfAbsent("createdBy", context.authenticatedUser().getId());
        reqmap.putIfAbsent("createdByName", context.authenticatedUser().getUsername());
        reqmap.putIfAbsent("createdByFullName", context.authenticatedUser().getDisplayName());
        reqmap.put("timestamp", Instant.now().toString());

        final String serializedResult = toApiJsonSerializer.serialize(reqmap);
        final HookEvent applicationEvent = new HookEvent(hookEventSource, serializedResult, appUser, ThreadLocalContextUtil.getContext());
        applicationContext.publishEvent(applicationEvent);
    }

}
