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

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.batch.exception.ErrorInfo;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.provider.CommandHandlerProvider;
import org.apache.fineract.commands.service.CommandSourceService;
import org.apache.fineract.commands.service.IdempotencyKeyResolver;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
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

        final AppUser appUser = context.authenticatedUser(CommandWrapper.wrap(actionName, entityName, null, null));

        final HookEventSource hookEventSource = new HookEventSource(entityName, actionName);

        // TODO: Add support for publishing array events
        if (command.json() != null) {
            Type type = new TypeToken<Map<String, Object>>() {

            }.getType();

            Map<String, Object> myMap;

            try {
                myMap = gson.fromJson(command.json(), type);
            } catch (Exception e) {
                throw new PlatformApiDataValidationException("error.msg.invalid.json", "The provided JSON is invalid.", new ArrayList<>(),
                        e);
            }

            Map<String, Object> reqmap = new HashMap<>();
            reqmap.put("entityName", entityName);
            reqmap.put("actionName", actionName);
            reqmap.put("createdBy", context.authenticatedUser().getId());
            reqmap.put("createdByName", context.authenticatedUser().getUsername());
            reqmap.put("createdByFullName", context.authenticatedUser().getDisplayName());

            reqmap.put("request", myMap);
            if (result instanceof CommandProcessingResult) {
                CommandProcessingResult resultCopy = CommandProcessingResult.fromCommandProcessingResult((CommandProcessingResult) result);

                reqmap.put("officeId", resultCopy.getOfficeId());
                reqmap.put("clientId", resultCopy.getClientId());
                resultCopy.setOfficeId(null);

                // Delegate special LOAN cases to a helper to avoid clutter
                Object maybeSpecialResponse = reshapeLoanResponseIfApplicable(entityName, actionName, resultCopy, type);
                if (maybeSpecialResponse != null) {
                    reqmap.put("response", maybeSpecialResponse);
                } else {
                    reqmap.put("response", resultCopy);
                }
            } else if (result instanceof ErrorInfo ex) {
                reqmap.put("status", "Exception");

                Map<String, Object> errorMap = new HashMap<>();

                try {
                    errorMap = gson.fromJson(ex.getMessage(), type);
                } catch (Exception e) {
                    errorMap.put("errorMessage", ex.getMessage());
                }

                errorMap.put("errorCode", ex.getErrorCode());
                errorMap.put("statusCode", ex.getStatusCode());

                reqmap.put("response", errorMap);
            }

            reqmap.put("timestamp", Instant.now().toString());

            final String serializedResult = toApiJsonSerializer.serialize(reqmap);

            final HookEvent applicationEvent = new HookEvent(hookEventSource, serializedResult, appUser,
                    ThreadLocalContextUtil.getContext());

            applicationContext.publishEvent(applicationEvent);
        }
    }

    /**
     * Reshape the response for specific LOAN actions by lifting certain fields from changes into the response root.
     * Returns a Map representing the response when reshaping is applied; otherwise returns null.
     */
    private Object reshapeLoanResponseIfApplicable(String entityName, String actionName, CommandProcessingResult resultCopy,
            Type gsonMapType) {
        if (!"LOAN".equalsIgnoreCase(entityName) || resultCopy == null) {
            return null;
        }
        if (resultCopy.getChanges() == null) {
            return null;
        }

        // DISBURSETOSAVINGS: move isDrawdown from changes -> root
        if ("DISBURSETOSAVINGS".equalsIgnoreCase(actionName) && resultCopy.getChanges().containsKey("isDrawdown")) {
            Map<String, Object> responseMap = gson.fromJson(gson.toJson(resultCopy), gsonMapType);
            @SuppressWarnings("unchecked")
            Map<String, Object> changesMap = (Map<String, Object>) responseMap.get("changes");
            if (changesMap != null && changesMap.containsKey("isDrawdown")) {
                responseMap.put("isDrawdown", changesMap.get("isDrawdown"));
                changesMap.remove("isDrawdown");
                if (changesMap.isEmpty()) {
                    responseMap.remove("changes");
                }
            }
            return responseMap;
        }

        // REPAYMENT: move isDrawdown and locId from changes -> root; unwrap Optional for locId to avoid Gson issues
        if ("REPAYMENT".equalsIgnoreCase(actionName)) {
            // Normalize Optional in changes
            if (resultCopy.getChanges().containsKey("locId")) {
                Object locIdRaw = resultCopy.getChanges().get("locId");
                if (locIdRaw instanceof java.util.Optional<?> opt) {
                    resultCopy.getChanges().put("locId", opt.orElse(null));
                }
            }
            Map<String, Object> responseMap = gson.fromJson(gson.toJson(resultCopy), gsonMapType);
            @SuppressWarnings("unchecked")
            Map<String, Object> changesMap = (Map<String, Object>) responseMap.get("changes");
            if (changesMap != null) {
                if (changesMap.containsKey("isDrawdown")) {
                    responseMap.put("isDrawdown", changesMap.get("isDrawdown"));
                    changesMap.remove("isDrawdown");
                }
                if (changesMap.containsKey("locId")) {
                    responseMap.put("locId", changesMap.get("locId"));
                    changesMap.remove("locId");
                }
                if (changesMap.isEmpty()) {
                    responseMap.remove("changes");
                }
            }
            return responseMap;
        }

        return null;
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
