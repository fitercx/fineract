package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleService extends LoanScheduleService {

    LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public CustomLoanScheduleService(LoanChargeService loanChargeService, ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanMapper loanMapper, LoanTransactionProcessingService loadTransactionProcessingService, LoanScheduleComponent loanSchedule,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(loanChargeService, reprocessLoanTransactionsService, loanMapper, loadTransactionProcessingService, loanSchedule);
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public void regenerateRepaymentSchedule(final Loan loan, final ScheduleGeneratorDTO scheduleGeneratorDTO) {
        final LoanScheduleModel loanScheduleModel = loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        if (loanScheduleModel == null) {
            return;
        }
        loanSchedule.updateLoanSchedule(loan, loanScheduleModel);
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isWaived()) {
                if (loan.isReceivableLocLoan()) {
                    Optional<LoanLineOfCreditParams> lloc = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
                    lloc.ifPresent(t -> loan.getLoanRepaymentScheduleDetail().setPrincipal(t.getApprovedReceivableAmount()));
                }

                loanChargeService.recalculateLoanCharge(loan, loanCharge, scheduleGeneratorDTO.getPenaltyWaitPeriod());

                if (loan.isReceivableLocLoan()) {
                    loan.getLoanRepaymentScheduleDetail().setPrincipal(loan.getApprovedPrincipal());
                }
            }
        }
    }

}
