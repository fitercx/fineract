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
package com.crediblex.fineract.portfolio.loanaccount.api;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.api.LoanChargesApiResource;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Configuration to register our custom LoanChargesApiResource and exclude the base one to prevent duplicate JAX-RS
 * resource registration.
 */
@Configuration
public class CredXLoanChargesApiResourceConfiguration {

    /**
     * Register our custom CredXLoanChargesApiResource to replace the base LoanChargesApiResource.
     */
    @Bean
    public CredXLoanChargesApiResource credXLoanChargesApiResource(PlatformSecurityContext context,
            ChargeReadPlatformService chargeReadPlatformService, LoanChargeReadPlatformService loanChargeReadPlatformService,
            DefaultToApiJsonSerializer<LoanChargeData> toApiJsonSerializer, ApiRequestParameterHelper apiRequestParameterHelper,
            PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            LoanReadPlatformService loanReadPlatformService) {
        return new CredXLoanChargesApiResource(context, chargeReadPlatformService, loanChargeReadPlatformService, toApiJsonSerializer,
                apiRequestParameterHelper, commandsSourceWritePlatformService, loanReadPlatformService);
    }

    /**
     * BeanFactoryPostProcessor to remove the base LoanChargesApiResource bean to prevent duplicate JAX-RS resource
     * registration.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static BeanFactoryPostProcessor removeLoanChargesApiResourceBeanPostProcessor() {
        return new BeanFactoryPostProcessor() {

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // Find and remove the base LoanChargesApiResource bean definition
                String[] beanNames = beanFactory.getBeanNamesForType(LoanChargesApiResource.class, false, false);
                for (String beanName : beanNames) {
                    if (beanFactory.getBeanDefinition(beanName).getBeanClassName() != null && beanFactory.getBeanDefinition(beanName)
                            .getBeanClassName().equals("org.apache.fineract.portfolio.loanaccount.api.LoanChargesApiResource")) {
                        ((org.springframework.beans.factory.support.BeanDefinitionRegistry) beanFactory).removeBeanDefinition(beanName);
                    }
                }
            }
        };
    }
}
