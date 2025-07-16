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
package com.crediblex.fineract.test.config;

import com.crediblex.fineract.test.initializer.CustomOrderedInitializerConfiguration;
import com.crediblex.fineract.test.initializer.CustomOrderedInitializerConfiguration.InitializerStepConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.test.config.CacheConfiguration;
import org.apache.fineract.test.helper.BusinessDateHelper;
import org.apache.fineract.test.initializer.base.FineractInitializer;
import org.apache.fineract.test.initializer.global.FineractGlobalInitializerStep;
import org.apache.fineract.test.initializer.scenario.FineractScenarioInitializerStep;
import org.apache.fineract.test.initializer.suite.FineractSuiteInitializerStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan({ 
    "org.apache.fineract.test.api", 
    "org.apache.fineract.test.helper", 
    "org.apache.fineract.test.support",
    "org.apache.fineract.test.initializer.global", // Include global initializer steps
    "com.crediblex.fineract.test" 
})
@PropertySource("classpath:fineract-test-application.properties")
@PropertySource("classpath:fineract-custom-test-application.properties")
@Import({ CacheConfiguration.class })
public class CustomFineractInitializerConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public CustomOrderedInitializerConfiguration customOrderedInitializerConfiguration() {
        CustomOrderedInitializerConfiguration config = new CustomOrderedInitializerConfiguration();
        config.configureGlobalSteps();
        return config;
    }

    @Bean
    public FineractInitializer fineractInitializer(
            BusinessDateHelper businessDateHelper,
            CustomOrderedInitializerConfiguration orderedConfig) {
        
        log.info("Creating custom FineractInitializer with ordered initialization steps");
        
        // Get enabled global steps from configuration
        List<FineractGlobalInitializerStep> globalSteps = createGlobalInitializerSteps(orderedConfig);
        
        // Log the steps that will be executed
        log.info("Enabled global initialization steps ({} total):", globalSteps.size());
        for (int i = 0; i < globalSteps.size(); i++) {
            log.info("  {}. {}", i + 1, globalSteps.get(i).getClass().getSimpleName());
        }
        
        return new FineractInitializer(
            globalSteps,
            Collections.emptyList(), // No suite initializers
            Collections.emptyList(), // No scenario initializers
            businessDateHelper
        );
    }
    
    private List<FineractGlobalInitializerStep> createGlobalInitializerSteps(
            CustomOrderedInitializerConfiguration orderedConfig) {
        
        List<FineractGlobalInitializerStep> steps = new ArrayList<>();
        
        // Get enabled steps from configuration
        List<InitializerStepConfig> enabledSteps = orderedConfig.getEnabledGlobalSteps();
        
        for (InitializerStepConfig stepConfig : enabledSteps) {
            try {
                // Get the bean from Spring context
                FineractGlobalInitializerStep step = applicationContext.getBean(stepConfig.getStepClass());
                
                // Wrap it in an ordered wrapper if needed
                OrderedInitializerStep orderedStep = new OrderedInitializerStep(step, stepConfig.getOrder());
                steps.add(orderedStep);
                
                log.debug("Added global step: {} with order {}", 
                    stepConfig.getStepClass().getSimpleName(), stepConfig.getOrder());
            } catch (Exception e) {
                log.error("Failed to create global step: {}", stepConfig.getStepClass().getName(), e);
            }
        }
        
        // Sort by order
        steps.sort(new AnnotationAwareOrderComparator());
        
        return steps;
    }
    
    /**
     * Wrapper class to add ordering to initializer steps
     */
    @Order
    private static class OrderedInitializerStep implements FineractGlobalInitializerStep {
        private final FineractGlobalInitializerStep delegate;
        private final int order;
        
        public OrderedInitializerStep(FineractGlobalInitializerStep delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }
        
        @Override
        public void initialize() throws Exception {
            delegate.initialize();
        }
        
        @Order
        public int getOrder() {
            return order;
        }
    }
    
    // Override to return empty lists for suite and scenario steps
    @Bean
    public List<FineractSuiteInitializerStep> suiteInitializerSteps() {
        return Collections.emptyList();
    }
    
    @Bean
    public List<FineractScenarioInitializerStep> scenarioInitializerSteps() {
        return Collections.emptyList();
    }
}
