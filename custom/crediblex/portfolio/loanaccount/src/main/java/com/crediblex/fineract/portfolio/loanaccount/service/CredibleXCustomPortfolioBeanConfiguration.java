package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CredibleXCustomPortfolioBeanConfiguration {

    @Bean
    LoanDisbursementService loanDisbursementService(LoanChargeValidator loanChargeValidator,
            LoanDisbursementValidator loanDisbursementValidator, ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        return new CustomLoanDisbursementService(loanChargeValidator, loanDisbursementValidator, reprocessLoanTransactionsService,
                loanLineOfCreditParamsRepository);
    }

    @Bean
    @Primary
    LoanChargeService loanChargeService(LoanChargeValidator loanChargeValidator,
            org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService loanTransactionProcessingService,
            CustomLatePaymentFeeCalculationService customLatePaymentFeeCalculationService) {
        return new CustomLoanChargeService(loanChargeValidator, loanTransactionProcessingService, customLatePaymentFeeCalculationService);
    }

    @Bean
    @Primary
    LoanChargeAssembler loanChargeAssembler(FromJsonHelper fromApiJsonHelper, ChargeRepositoryWrapper chargeRepository,
            LoanChargeRepository loanChargeRepository, LoanProductRepository loanProductRepository, ExternalIdFactory externalIdFactory,
            CustomLatePaymentFeeCalculationService customLatePaymentFeeCalculationService) {
        return new CustomLoanChargeAssembler(fromApiJsonHelper, chargeRepository, loanChargeRepository, loanProductRepository,
                externalIdFactory, customLatePaymentFeeCalculationService);
    }
}
