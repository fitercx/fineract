package com.crediblex.fineract.portfolio.loanaccount.service;

import java.time.LocalDate;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleDTO;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of LoanScheduleService that ensures charges are recalculated BEFORE schedule generation to
 * ensure LOC-specific late payment fees are included in the schedule.
 */
@Service
@Slf4j
public class CustomLoanScheduleService extends LoanScheduleService {

    private final org.apache.fineract.portfolio.loanaccount.service.LoanChargeService loanChargeService;
    private final LoanMapper loanMapper;
    private final LoanScheduleComponent loanSchedule;
    private final LoanTransactionProcessingService loadTransactionProcessingService;

    @Autowired
    public CustomLoanScheduleService(org.apache.fineract.portfolio.loanaccount.service.LoanChargeService loanChargeService,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanMapper loanMapper,
            LoanTransactionProcessingService loadTransactionProcessingService, LoanScheduleComponent loanSchedule) {
        super(loanChargeService, reprocessLoanTransactionsService, loanMapper, loadTransactionProcessingService, loanSchedule);
        this.loanChargeService = loanChargeService;
        this.loanMapper = loanMapper;
        this.loanSchedule = loanSchedule;
        this.loadTransactionProcessingService = loadTransactionProcessingService;
    }

    /**
     * Override to recalculate charges BEFORE generating the schedule model. This ensures that LOC-specific late payment
     * fees are included in the schedule.
     */
    @Override
    public void regenerateRepaymentSchedule(final Loan loan, final ScheduleGeneratorDTO scheduleGeneratorDTO) {
        // Recalculate all charges with our custom logic
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isWaived()) {
                loanChargeService.recalculateLoanCharge(loan, loanCharge, scheduleGeneratorDTO.getPenaltyWaitPeriod());
            }
        }

        // Generate the schedule model with the updated charges
        final LoanScheduleModel loanScheduleModel = loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        if (loanScheduleModel == null) {
            return;
        }

        // Update the loan schedule
        loanSchedule.updateLoanSchedule(loan, loanScheduleModel);
    }

    @Override
    public void recalculateSchedule(final Loan loan, final ScheduleGeneratorDTO generatorDTO) {
        super.recalculateSchedule(loan, generatorDTO);
    }

    /**
     * Override to recalculate charges BEFORE generating the schedule model with interest recalculation.
     */
    @Override
    public void regenerateRepaymentScheduleWithInterestRecalculation(final Loan loan, final ScheduleGeneratorDTO generatorDTO) {
        final LocalDate lastTransactionDate = loan.getLastUserTransactionDate();
        // Recalculate charges with our custom logic
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isDueAtDisbursement()) {
                loan.updateOverdueScheduleInstallment(loanCharge);
                if (loanCharge.getDueLocalDate() == null || (!loan.getLastRepaymentPeriodDueDate(true).isAfter(loanCharge.getDueLocalDate())
                        || loan.getLoanProductRelatedDetail().getLoanScheduleType().equals(LoanScheduleType.PROGRESSIVE))) {
                    if ((loanCharge.isInstalmentFee() || !loanCharge.isWaived())
                            && (loanCharge.getDueLocalDate() == null || !lastTransactionDate.isAfter(loanCharge.getDueLocalDate()))) {
                        loanChargeService.recalculateLoanCharge(loan, loanCharge, generatorDTO.getPenaltyWaitPeriod());
                        loanCharge.updateWaivedAmount(loan.getCurrency());
                    }
                } else {
                    loanCharge.setActive(false);
                }
            }
        }

        // Get the recalculated schedule
        final LoanScheduleDTO loanScheduleDTO = loadTransactionProcessingService.getRecalculatedSchedule(generatorDTO, loan);
        if (loanScheduleDTO == null) {
            return;
        }

        // Update the loan schedule
        loan.setInterestRecalculatedOn(org.apache.fineract.infrastructure.core.service.DateUtils.getBusinessLocalDate());
        if (loanScheduleDTO.getInstallments() != null) {
            loanSchedule.updateLoanSchedule(loan, loanScheduleDTO.getInstallments());
        } else {
            loanSchedule.updateLoanSchedule(loan, loanScheduleDTO.getLoanScheduleModel());
        }
    }
}
