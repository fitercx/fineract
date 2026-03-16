package com.crediblex.fineract.portfolio.loanaccount.service.configuration;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CredXFineractStyleLoanRepaymentScheduleTransactionProcessor;
import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CredXProRataFineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CustomLoanAccountStarter {

    /**
     * CredX processor for mifos-standard-strategy. LPI same-month (late fee on same month's row) only for loans
     * disbursed on or after custom.lpi-same-month.enabled-from-date; existing loans unchanged.
     */
    @Bean
    @Primary
    public CredXFineractStyleLoanRepaymentScheduleTransactionProcessor fineractStyleProcessor(ExternalIdFactory externalIdFactory,
            LpiSameMonthProperties lpiSameMonthProperties) {
        return new CredXFineractStyleLoanRepaymentScheduleTransactionProcessor(externalIdFactory, lpiSameMonthProperties);
    }

    /**
     * CredX processor for pro-rata-mifos-standard-strategy. LPI same-month only for loans disbursed on or after
     * custom.lpi-same-month.enabled-from-date; existing loans unchanged.
     */
    @Bean
    @Primary
    public CredXProRataFineractStyleLoanRepaymentScheduleTransactionProcessor proRataInterestProcessor(ExternalIdFactory externalIdFactory,
            LpiSameMonthProperties lpiSameMonthProperties) {
        return new CredXProRataFineractStyleLoanRepaymentScheduleTransactionProcessor(externalIdFactory, lpiSameMonthProperties);
    }
}
