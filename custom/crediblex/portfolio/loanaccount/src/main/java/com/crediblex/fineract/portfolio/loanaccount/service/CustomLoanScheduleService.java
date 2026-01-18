package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.math.BigDecimal;
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
import lombok.extern.slf4j.Slf4j;

@Service
@Primary
@Slf4j
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
        // Check if this is a LOC Receivable loan by checking LOC params directly
        // The isReceivableLocLoan field might not be set yet
        // Note: During loan creation, the loan might not have an ID yet, so check for null
        Optional<LoanLineOfCreditParams> locParams = Optional.empty();
        boolean isReceivableLocLoan = false;
        if (loan.getId() != null) {
            locParams = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
            isReceivableLocLoan = locParams.isPresent() 
                    && locParams.get().getLineOfCredit().getProductType().isReceivable();
        } else {
            // If loan ID is null (during creation), fall back to the field value
            isReceivableLocLoan = loan.isReceivableLocLoan();
        }
        
        // For LOC Receivable loans, set principal to amountAfterAdvance BEFORE generating schedule model
        // This ensures the schedule model is generated with the correct principal (proposed amount, not approved amount)
        if (isReceivableLocLoan && locParams.isPresent()) {
            BigDecimal amountAfterAdvance = locParams.get().getAmountAfterAdvance();
            loan.getLoanRepaymentScheduleDetail().setPrincipal(amountAfterAdvance);
        }
        
        final LoanScheduleModel loanScheduleModel = loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        if (loanScheduleModel == null) {
            return;
        }
        
        loanSchedule.updateLoanSchedule(loan, loanScheduleModel);
        
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isWaived()) {
                loanChargeService.recalculateLoanCharge(loan, loanCharge, scheduleGeneratorDTO.getPenaltyWaitPeriod());
            }
        }
        
        // After all charges are recalculated, restore principal to approved amount for LOC Receivable loans
        // This ensures the final principal matches the approved/disbursed amount
        if (isReceivableLocLoan) {
            BigDecimal approvedPrincipal = loan.getApprovedPrincipal();
            loan.getLoanRepaymentScheduleDetail().setPrincipal(approvedPrincipal);
        }
    }

}
