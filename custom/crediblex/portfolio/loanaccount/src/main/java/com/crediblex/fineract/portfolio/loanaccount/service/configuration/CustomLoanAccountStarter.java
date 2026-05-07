package com.crediblex.fineract.portfolio.loanaccount.service.configuration;

import com.crediblex.fineract.portfolio.loanaccount.data.CredXLoanRescheduleRequestDataValidator;
import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestDataValidator;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Bean("loanRescheduleRequestDataValidator")
    @Primary
    public LoanRescheduleRequestDataValidator loanRescheduleRequestDataValidator(final FromJsonHelper fromJsonHelper,
            @Qualifier("progressiveLoanRescheduleRequestDataValidatorImpl") final LoanRescheduleRequestDataValidator progressiveValidator) {
        return new CredXLoanRescheduleRequestDataValidator(fromJsonHelper, progressiveValidator);
    }
}
