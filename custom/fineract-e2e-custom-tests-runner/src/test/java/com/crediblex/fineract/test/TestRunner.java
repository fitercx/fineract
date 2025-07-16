package com.crediblex.fineract.test;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/features",
    glue = {
        // Our custom hook that overrides the base hook
        "org.apache.fineract.test.stepdef.hook",
        // Specific step definitions we want to use
        "org.apache.fineract.test.stepdef.common",
        "org.apache.fineract.test.stepdef.loan",
        "org.apache.fineract.test.stepdef.saving", // Can include this now since we changed method names
        // Our custom packages
        "com.crediblex.fineract.test.stepdef",
        "com.crediblex.fineract.test.config"
    },
    plugin = {
        "pretty",
        "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
    },
    tags = "not @ignore"
)
public class TestRunner {}
