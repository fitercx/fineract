package com.crediblex.fineract.portfolio.loanaccount.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleDTO;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Custom implementation of LoanScheduleService that ensures charges are recalculated
 * BEFORE schedule generation to ensure LOC-specific late payment fees are included in the schedule.
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
                                   ReprocessLoanTransactionsService reprocessLoanTransactionsService,
                                   LoanMapper loanMapper,
                                   LoanTransactionProcessingService loadTransactionProcessingService,
                                   LoanScheduleComponent loanSchedule) {
        super(loanChargeService, reprocessLoanTransactionsService, loanMapper, loadTransactionProcessingService, loanSchedule);
        this.loanChargeService = loanChargeService;
        this.loanMapper = loanMapper;
        this.loanSchedule = loanSchedule;
        this.loadTransactionProcessingService = loadTransactionProcessingService;
        log.info("🚀 CustomLoanScheduleService initialized successfully!");
    }

    /**
     * Override to recalculate charges BEFORE generating the schedule model.
     * This ensures that LOC-specific late payment fees are included in the schedule.
     */
    @Override
    public void regenerateRepaymentSchedule(final Loan loan, final ScheduleGeneratorDTO scheduleGeneratorDTO) {
        log.info("=== REGENERATING REPAYMENT SCHEDULE ===");
        log.info("Loan ID: {}, Penalty Wait Period: {}", loan.getId(), scheduleGeneratorDTO.getPenaltyWaitPeriod());
        
        // FIRST: Recalculate all charges with our custom logic
        final Set<LoanCharge> charges = loan.getActiveCharges();
        log.info("Found {} active charges for loan {}", charges.size(), loan.getId());
        
        for (final LoanCharge loanCharge : charges) {
            log.info("Processing charge ID: {}, Name: '{}', Is Waived: {}, Is Overdue Installment: {}", 
                    loanCharge.getId(), loanCharge.getCharge().getName(), 
                    loanCharge.isWaived(), loanCharge.isOverdueInstallmentCharge());
            
            if (!loanCharge.isWaived()) {
                log.info("Recalculating charge {} for loan {} before schedule generation", 
                         loanCharge.getId(), loan.getId());
                loanChargeService.recalculateLoanCharge(loan, loanCharge, scheduleGeneratorDTO.getPenaltyWaitPeriod());
            } else {
                log.info("Skipping waived charge {}", loanCharge.getId());
            }
        }
        
        // SECOND: Generate the schedule model with the updated charges
        log.info("Generating schedule model for loan {}", loan.getId());
        final LoanScheduleModel loanScheduleModel = 
            loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        if (loanScheduleModel == null) {
            log.warn("No schedule model generated for loan {}", loan.getId());
            log.info("=== END REGENERATING REPAYMENT SCHEDULE (NO MODEL) ===");
            return;
        }
        
        // THIRD: Update the loan schedule
        log.info("Updating loan schedule for loan {}", loan.getId());
        loanSchedule.updateLoanSchedule(loan, loanScheduleModel);
        
        log.info("Successfully regenerated repayment schedule for loan {} with custom charge amounts", loan.getId());
        log.info("=== END REGENERATING REPAYMENT SCHEDULE ===");
    }

    @Override
    public void recalculateSchedule(final Loan loan, final ScheduleGeneratorDTO generatorDTO) {
        log.info("=== RECALCULATING SCHEDULE ===");
        log.info("Loan ID: {}, Is Interest Bearing: {}, Is Charged Off: {}", 
                loan.getId(), loan.isInterestBearingAndInterestRecalculationEnabled(), loan.isChargedOff());
        super.recalculateSchedule(loan, generatorDTO);
        log.info("=== END RECALCULATING SCHEDULE ===");
    }

    /**
     * Override to recalculate charges BEFORE generating the schedule model with interest recalculation.
     */
    @Override
    public void regenerateRepaymentScheduleWithInterestRecalculation(final Loan loan, final ScheduleGeneratorDTO generatorDTO) {
        log.debug("Regenerating repayment schedule with interest recalculation for loan {} with custom charge recalculation", loan.getId());
        
        final LocalDate lastTransactionDate = loan.getLastUserTransactionDate();
        
        // FIRST: Recalculate charges with our custom logic
        final Set<LoanCharge> charges = loan.getActiveCharges();
        for (final LoanCharge loanCharge : charges) {
            if (!loanCharge.isDueAtDisbursement()) {
                loan.updateOverdueScheduleInstallment(loanCharge);
                if (loanCharge.getDueLocalDate() == null || (!loan.getLastRepaymentPeriodDueDate(true).isAfter(loanCharge.getDueLocalDate())
                        || loan.getLoanProductRelatedDetail().getLoanScheduleType().equals(LoanScheduleType.PROGRESSIVE))) {
                    if ((loanCharge.isInstalmentFee() || !loanCharge.isWaived()) && (loanCharge.getDueLocalDate() == null
                            || !lastTransactionDate.isAfter(loanCharge.getDueLocalDate()))) {
                        log.debug("Recalculating charge {} for loan {} before interest recalculation schedule generation", 
                                 loanCharge.getId(), loan.getId());
                        loanChargeService.recalculateLoanCharge(loan, loanCharge, generatorDTO.getPenaltyWaitPeriod());
                        loanCharge.updateWaivedAmount(loan.getCurrency());
                    }
                } else {
                    loanCharge.setActive(false);
                }
            }
        }
        
        // SECOND: Get the recalculated schedule
        final LoanScheduleDTO loanScheduleDTO = loadTransactionProcessingService.getRecalculatedSchedule(generatorDTO, loan);
        if (loanScheduleDTO == null) {
            log.warn("No schedule DTO generated for loan {} with interest recalculation", loan.getId());
            return;
        }
        
        // THIRD: Update the loan schedule
        loan.setInterestRecalculatedOn(org.apache.fineract.infrastructure.core.service.DateUtils.getBusinessLocalDate());
        if (loanScheduleDTO.getInstallments() != null) {
            loanSchedule.updateLoanSchedule(loan, loanScheduleDTO.getInstallments());
        } else {
            loanSchedule.updateLoanSchedule(loan, loanScheduleDTO.getLoanScheduleModel());
        }
        
        log.debug("Successfully regenerated repayment schedule with interest recalculation for loan {} with custom charge amounts", loan.getId());
    }
}
