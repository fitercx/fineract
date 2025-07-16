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
package com.crediblex.fineract.test.initializer;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.test.initializer.global.FineractGlobalInitializerStep;
import org.springframework.core.annotation.Order;

/**
 * Custom ordered initialization configuration that allows selective inclusion of initialization steps
 */
@Slf4j
public class CustomOrderedInitializerConfiguration {
    
    private final List<InitializerStepConfig> globalSteps = new ArrayList<>();
    
    /**
     * Configuration for an initializer step with order
     */
    public static class InitializerStepConfig {
        private final Class<? extends FineractGlobalInitializerStep> stepClass;
        private final int order;
        private final boolean enabled;
        
        public InitializerStepConfig(Class<? extends FineractGlobalInitializerStep> stepClass, int order, boolean enabled) {
            this.stepClass = stepClass;
            this.order = order;
            this.enabled = enabled;
        }
        
        public Class<? extends FineractGlobalInitializerStep> getStepClass() {
            return stepClass;
        }
        
        public int getOrder() {
            return order;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
    }
    
    /**
     * Define the global initialization steps and their order
     * This allows us to selectively enable/disable steps while maintaining order
     */
    public void configureGlobalSteps() {

        // Global configurations
        addGlobalStep(org.apache.fineract.test.initializer.global.GlobalConfigurationGlobalInitializerStep.class, 100, true);

        // Currency configuration - often needed as base configuration
        addGlobalStep(org.apache.fineract.test.initializer.global.CurrencyGlobalInitializerStep.class, 200, true);
        
        // Code values - needed for various dropdowns and configurations
        addGlobalStep(org.apache.fineract.test.initializer.global.CodeGlobalInitializerStep.class, 300, true);
        
        // Payment types - needed for transactions
        addGlobalStep(org.apache.fineract.test.initializer.global.PaymentTypeGlobalInitializerStep.class, 400, true);
        
        // Funds - needed for accounting
        addGlobalStep(org.apache.fineract.test.initializer.global.FundGlobalInitializerStep.class, 500, true);
        
        // Chart of Accounts - needed for accounting
        addGlobalStep(org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.class, 600, true);
        
        // Financial activity mappings
        addGlobalStep(org.apache.fineract.test.initializer.global.FinancialActivityMappingGlobalInitializerStep.class, 700, true);
        
        // Charges - needed for fees
        addGlobalStep(org.apache.fineract.test.initializer.global.ChargeGlobalInitializerStep.class, 800, true);
        

        // Scheduler jobs
        addGlobalStep(org.apache.fineract.test.initializer.global.SchedulerGlobalInitializerStep.class, 900, true);
        
        // Business step configuration for COB
        addGlobalStep(org.apache.fineract.test.initializer.global.CobBusinessStepInitializerStep.class, 1000, false);
        
        // Delinquency buckets - disable by default
        addGlobalStep(org.apache.fineract.test.initializer.global.DelinquencyGlobalInitializerStep.class, 1100, false);
        
        // Datatables - disable by default
        addGlobalStep(org.apache.fineract.test.initializer.global.DatatablesGlobalInitializerStep.class, 1200, false);
        
        // Loan products - disable as we want to create on-demand
        addGlobalStep(org.apache.fineract.test.initializer.global.LoanProductGlobalInitializerStep.class, 1300, false);
        
        // Savings products - disable as we want to create on-demand
        addGlobalStep(org.apache.fineract.test.initializer.global.SavingsProductGlobalInitializer.class, 1400, false);
    }
    
    private void addGlobalStep(Class<? extends FineractGlobalInitializerStep> stepClass, int order, boolean enabled) {
        globalSteps.add(new InitializerStepConfig(stepClass, order, enabled));
        log.info("Configured global step: {} with order {} - {}", 
            stepClass.getSimpleName(), order, enabled ? "ENABLED" : "DISABLED");
    }
    
    public List<InitializerStepConfig> getGlobalSteps() {
        return new ArrayList<>(globalSteps);
    }
    
    public List<InitializerStepConfig> getEnabledGlobalSteps() {
        return globalSteps.stream()
            .filter(InitializerStepConfig::isEnabled)
            .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
            .toList();
    }
}
