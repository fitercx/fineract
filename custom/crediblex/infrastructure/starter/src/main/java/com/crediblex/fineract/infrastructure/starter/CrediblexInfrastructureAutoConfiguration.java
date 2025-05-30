package com.crediblex.fineract.infrastructure.starter;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.boot.autoconfigure.AutoConfiguration;

@AutoConfiguration
@ComponentScans({
        @ComponentScan("com.crediblex.fineract.infrastructure.datatables"),
        @ComponentScan("com.crediblex.fineract.infrastructure.codes")
})
public class CrediblexInfrastructureAutoConfiguration {
}