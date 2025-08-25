package com.crediblex.fineract.portfolio.account.service;

import org.apache.fineract.commands.provider.CommandHandlerProvider;
import org.apache.fineract.commands.service.CommandSourceService;
import org.apache.fineract.commands.service.IdempotencyKeyResolver;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.hooks.event.HookEvent;
import org.apache.fineract.infrastructure.hooks.event.HookEventSource;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
public class CustomCommandProcessingService extends SynchronousCommandProcessingService {

    public CustomCommandProcessingService(PlatformSecurityContext context, ApplicationContext applicationContext,
                                          ToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer,
                                          ToApiJsonSerializer<CommandProcessingResult> toApiResultJsonSerializer,
                                          ConfigurationDomainService configurationDomainService, CommandHandlerProvider commandHandlerProvider,
                                          IdempotencyKeyResolver idempotencyKeyResolver, CommandSourceService commandSourceService,
                                          FineractRequestContextHolder fineractRequestContextHolder) {
        super(context, applicationContext, toApiJsonSerializer, toApiResultJsonSerializer, configurationDomainService,
                commandHandlerProvider, idempotencyKeyResolver, commandSourceService, fineractRequestContextHolder);
    }

    @Override
    public void publishHookEvent(final String entityName, final String actionName, JsonCommand command, final Object result) {
        super.publishHookEvent(entityName, actionName, command, result);
    }
}
