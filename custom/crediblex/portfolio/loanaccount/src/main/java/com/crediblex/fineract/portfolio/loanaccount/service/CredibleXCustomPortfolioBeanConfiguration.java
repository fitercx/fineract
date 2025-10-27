package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredibleXCustomPortfolioBeanConfiguration {

    @Bean
    LoanDisbursementService loanDisbursementService(LoanChargeValidator loanChargeValidator,
            LoanDisbursementValidator loanDisbursementValidator, ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        return new CustomLoanDisbursementService(loanChargeValidator, loanDisbursementValidator, reprocessLoanTransactionsService,
                loanLineOfCreditParamsRepository);
    }

}
