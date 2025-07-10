package com.crediblex.fineract.test.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = TestApplicationConfiguration.class)
public class TestCucumberConfiguration {}
