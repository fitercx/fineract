package com.crediblex.fineract.portfolio.starter;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.boot.autoconfigure.AutoConfiguration;

@AutoConfiguration
@ComponentScans({
        @ComponentScan("com.crediblex.fineract.portfolio.client"),
        @ComponentScan("com.crediblex.fineract.portfolio.loanaccount")
})
public class CrediblexPortfolioAutoConfiguration {
}