package com.crediblex.fineract.portfolio.account.service;

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

@Service
@Primary
public class CustomCommandProcessingService extends SynchronousCommandProcessingService {

    public CustomCommandProcessingService(PlatformSecurityContext context, ApplicationContext applicationContext,
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

                // For the specific case of loan disbursement to savings, we want to move the isDrawdown field from
                // changes to the root of the response
                if ("LOAN".equalsIgnoreCase(entityName) && "DISBURSETOSAVINGS".equalsIgnoreCase(actionName)
                        && resultCopy.getChanges() != null && resultCopy.getChanges().containsKey("isDrawdown")) {
                    Map<String, Object> responseMap = gson.fromJson(gson.toJson(resultCopy), type);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> changesMap = (Map<String, Object>) responseMap.get("changes");
                    if (changesMap != null && changesMap.containsKey("isDrawdown")) {
                        responseMap.put("isDrawdown", changesMap.get("isDrawdown"));
                        changesMap.remove("isDrawdown");
                        if (changesMap.isEmpty()) {
                            responseMap.remove("changes");
                        }
                    }
                    reqmap.put("response", responseMap);
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
}
