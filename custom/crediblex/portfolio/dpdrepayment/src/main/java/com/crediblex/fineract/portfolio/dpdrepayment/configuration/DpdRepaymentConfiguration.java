package com.crediblex.fineract.portfolio.dpdrepayment.configuration;

import com.crediblex.fineract.portfolio.dpdrepayment.domain.transactionprocessor.DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DpdRepaymentConfiguration {

    @Bean
    public DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor dpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor(
            ExternalIdFactory externalIdFactory) {
        return new DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor(externalIdFactory);
    }
}
