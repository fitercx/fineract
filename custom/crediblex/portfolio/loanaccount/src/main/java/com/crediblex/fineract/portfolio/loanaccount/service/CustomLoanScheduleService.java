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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleService extends LoanScheduleService {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanScheduleService.class);

    LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public CustomLoanScheduleService(LoanChargeService loanChargeService, ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanMapper loanMapper, LoanTransactionProcessingService loadTransactionProcessingService, LoanScheduleComponent loanSchedule,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(loanChargeService, reprocessLoanTransactionsService, loanMapper, loadTransactionProcessingService, loanSchedule);
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public void regenerateRepaymentSchedule(final Loan loan, final ScheduleGeneratorDTO scheduleGeneratorDTO) {
        // For LOC Receivable loans, ensure principal is set to proposed principal (amountAfterAdvance) BEFORE
        // generating the schedule model. This ensures that the schedule is generated with correct charge amounts
        // calculated from proposed principal (81,000) instead of approved principal (66,194.31).
        // Note: We check LoanLineOfCreditParams directly instead of loan.isReceivableLocLoan() because
        // the flag might not be set yet at this point (it's set during constructLoanApplicationTerms).
        log.info("Loan {}: regenerateRepaymentSchedule called. isReceivableLocLoan={}, current principal={}", loan.getId(),
                loan.isReceivableLocLoan(), loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount());

        BigDecimal savedPrincipalForRestore = null;
        // Note: This repository lookup may be redundant if called from
        // CustomLoanScheduleAssembler.assembleLoanApproval()
        // which already fetches LoanLineOfCreditParams. However, we fetch here to keep this method self-contained
        // and reusable from other callers. Consider passing Optional<LoanLineOfCreditParams> as a parameter
        // or caching it on the loan entity to optimize in the future.
        Optional<LoanLineOfCreditParams> loanLineOfCreditParams = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());

        // Check if this is a LOC Receivable loan by checking LoanLineOfCreditParams
        boolean isLocReceivable = loanLineOfCreditParams.isPresent() && loanLineOfCreditParams.get().getLineOfCredit() != null
                && loanLineOfCreditParams.get().getLineOfCredit().getProductType() != null
                && loanLineOfCreditParams.get().getLineOfCredit().getProductType().isReceivable();

        if (isLocReceivable) {
            log.info(
                    "Loan {}: LOC Receivable detected via LoanLineOfCreditParams. LoanLineOfCreditParams present={}, amountAfterAdvance={}",
                    loan.getId(), loanLineOfCreditParams.isPresent(),
                    loanLineOfCreditParams.map(LoanLineOfCreditParams::getAmountAfterAdvance).orElse(null));

            if (loanLineOfCreditParams.get().getAmountAfterAdvance() != null) {
                // Save the approved principal (the value we should restore to), not the current principal
                // because the current principal might already be set to proposed principal by
                // CustomLoanScheduleAssembler
                savedPrincipalForRestore = loan.getApprovedPrincipal();
                BigDecimal proposedPrincipal = loanLineOfCreditParams.get().getAmountAfterAdvance();
                BigDecimal currentPrincipal = loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount();
                log.info(
                        "LOC Receivable loan {}: Setting principal to proposed principal {} (from current principal {}) before schedule model generation. Will restore to approved principal {}",
                        loan.getId(), proposedPrincipal, currentPrincipal, savedPrincipalForRestore);
                loan.getLoanRepaymentScheduleDetail().setPrincipal(proposedPrincipal);
            } else {
                log.info("Loan {}: Skipping principal update - amountAfterAdvance is null", loan.getId());
            }
        } else {
            log.info("Loan {}: Not a LOC Receivable loan (checked via LoanLineOfCreditParams), skipping principal adjustment",
                    loan.getId());
        }

        // Generate schedule model with principal set to proposed principal (if LOC Receivable)
        log.info("Loan {}: Generating schedule model with principal: {}", loan.getId(),
                loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount());
        final LoanScheduleModel loanScheduleModel = loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        if (loanScheduleModel == null) {
            // Restore principal before returning
            if (isLocReceivable && savedPrincipalForRestore != null) {
                loan.getLoanRepaymentScheduleDetail().setPrincipal(savedPrincipalForRestore);
            }
            return;
        }
        loanSchedule.updateLoanSchedule(loan, loanScheduleModel);

        // Recalculate charges with principal still set to proposed principal (if LOC Receivable)
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isWaived()) {
                loanChargeService.recalculateLoanCharge(loan, loanCharge, scheduleGeneratorDTO.getPenaltyWaitPeriod());
            }
        }

        // Restore principal to approved amount for LOC Receivable loans after schedule regeneration
        if (isLocReceivable && savedPrincipalForRestore != null) {
            log.info("LOC Receivable loan {}: Restoring principal to approved principal {} after schedule regeneration", loan.getId(),
                    savedPrincipalForRestore);
            loan.getLoanRepaymentScheduleDetail().setPrincipal(savedPrincipalForRestore);
            // Update derived fields after principal restoration to ensure schedule status is correctly calculated
            loan.updateLoanScheduleDependentDerivedFields();
            loan.updateLoanSummaryDerivedFields();
        }
    }

}
