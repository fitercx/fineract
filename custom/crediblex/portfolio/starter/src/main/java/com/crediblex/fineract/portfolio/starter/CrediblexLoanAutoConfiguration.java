package com.crediblex.fineract.portfolio.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;

@AutoConfiguration
@ComponentScans({
        @ComponentScan("com.crediblex.fineract.portfolio.loanaccount")
})
public class CrediblexLoanAutoConfiguration {
}
