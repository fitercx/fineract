package com.crediblex.fineract.portfolio.loanaccount.service.configuration;

import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CustomLoanAccountStarter {

    @Bean
    @Primary
    public ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor proRataInterestProcessor(ExternalIdFactory externalIdFactory) {
        return new ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor(externalIdFactory);
    }
}
