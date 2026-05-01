package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.interestpauses.service.LoanAccountTransfersService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanRefundValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDownPaymentHandlerService;
import org.apache.fineract.portfolio.loanaccount.service.LoanRefundService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.ReplayedTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CredibleXCustomPortfolioBeanConfiguration {

    /**
     * Replaces the base {@code ReprocessLoanTransactionsServiceImpl} so that {@code removeLoanCharge} also re-applies
     * the CredX same-month LPI wrapper after a charge is deleted. Without this, deleting an unpaid LPI charge would
     * leave the schedule stamped with the default date-based wrapper (the parent's {@code removeLoanCharge} only calls
     * {@code reprocessTransactions} — which the ThreadLocal fix routes through the CredX wrapper — when the charge was
     * paid or partially paid; unpaid deletions skip that path entirely).
     */
    @Bean
    @Primary
    ReprocessLoanTransactionsService reprocessLoanTransactionsService(LoanAccountService loanAccountService,
            LoanAccountTransfersService loanAccountTransfersService,
            ReplayedTransactionBusinessEventService replayedTransactionBusinessEventService,
            LoanTransactionProcessingService loanTransactionProcessingService, LpiSameMonthProperties lpiSameMonthProperties) {
        return new CredXReprocessLoanTransactionsService(loanAccountService, loanAccountTransfersService,
                replayedTransactionBusinessEventService, loanTransactionProcessingService, lpiSameMonthProperties);
    }

    @Bean
    LoanDisbursementService loanDisbursementService(LoanChargeValidator loanChargeValidator,
            LoanDisbursementValidator loanDisbursementValidator, ReprocessLoanTransactionsService reprocessLoanTransactionsService) {
        return new CustomLoanDisbursementService(loanChargeValidator, loanDisbursementValidator, reprocessLoanTransactionsService);
    }

    @Bean
    LoanDownPaymentHandlerService loanDownPaymentHandlerService(LoanTransactionRepository loanTransactionRepository,
            BusinessEventNotifierService businessEventNotifierService,
            LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanScheduleService loanScheduleService,
            LoanRefundService loanRefundService, LoanRefundValidator loanRefundValidator,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanTransactionProcessingService loanTransactionProcessingService, LpiSameMonthProperties lpiSameMonthProperties) {
        return new CustomLoanDownPaymentHandlerService(loanTransactionRepository, businessEventNotifierService,
                loanDownPaymentTransactionValidator, loanScheduleService, loanRefundService, loanRefundValidator,
                reprocessLoanTransactionsService, loanTransactionProcessingService, lpiSameMonthProperties);
    }

}
