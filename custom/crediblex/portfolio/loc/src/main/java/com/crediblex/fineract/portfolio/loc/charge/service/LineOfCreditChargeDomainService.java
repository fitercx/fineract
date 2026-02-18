package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.apache.fineract.portfolio.tax.service.TaxUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless domain service encapsulating LOC charge lifecycle logic.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LineOfCreditChargeDomainService {

    private final JournalEntryWritePlatformService journalEntryWritePlatformService;

    public LineOfCreditCharge create(LineOfCredit loc, Charge definition, BigDecimal overrideAmount) {
        ChargeTimeType timeType = ChargeTimeType.fromInt(definition.getChargeTimeType());
        ChargeCalculationType calcType = ChargeCalculationType.fromInt(definition.getChargeCalculation());
        BigDecimal baseAmount = overrideAmount != null ? overrideAmount : definition.getAmount();
        BigDecimal chargeAmount = baseAmount;

        if (calcType.isPercentageOfAmount()) {
            chargeAmount = loc.getMaximumAmount().multiply(baseAmount).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        }

        LineOfCreditCharge lineOfCreditCharge = new LineOfCreditCharge();
        lineOfCreditCharge.setLineOfCredit(loc);
        lineOfCreditCharge.setChargeDefinition(definition);
        lineOfCreditCharge.setPenaltyCharge(definition.isPenalty());
        lineOfCreditCharge.setChargeTime(timeType.getValue());
        lineOfCreditCharge.setChargeCalculation(calcType.getValue());
        lineOfCreditCharge.setActive(true);
        lineOfCreditCharge.setAmountPaid(BigDecimal.ZERO);
        lineOfCreditCharge.setAmountWaived(BigDecimal.ZERO);
        lineOfCreditCharge.setAmountWrittenOff(BigDecimal.ZERO);
        lineOfCreditCharge.setWaived(false);

        if (calcType == ChargeCalculationType.FLAT) {
            lineOfCreditCharge.setAmount(chargeAmount);
            lineOfCreditCharge.setPercentage(null);
            lineOfCreditCharge.setAmountPercentageAppliedTo(null);
        } else { // PERCENT_OF_AMOUNT
            lineOfCreditCharge.setPercentage(baseAmount);
            lineOfCreditCharge.setAmountPercentageAppliedTo(loc.getMaximumAmount());
            lineOfCreditCharge.setAmount(chargeAmount);
        }

        lineOfCreditCharge.setAmountOutstanding(lineOfCreditCharge.getAmount());
        lineOfCreditCharge.setPaid(lineOfCreditCharge.getAmountOutstanding().compareTo(BigDecimal.ZERO) == 0);

        updateTaxAmount(definition, lineOfCreditCharge, DateUtils.getBusinessLocalDate(), lineOfCreditCharge.getAmount());
        return lineOfCreditCharge;
    }

    public void applyPercentBase(LineOfCreditCharge lineOfCreditCharge, BigDecimal base) {
        if (isPercent(lineOfCreditCharge)) {
            lineOfCreditCharge.setAmountPercentageAppliedTo(base);
            BigDecimal amount = base.multiply(lineOfCreditCharge.getPercentage()).divide(BigDecimal.valueOf(100), 3,
                    java.math.RoundingMode.HALF_UP);
            lineOfCreditCharge.setAmount(amount);
            recalculateOutstanding(lineOfCreditCharge);
        }
    }

    public void update(LineOfCreditCharge lineOfCreditCharge, BigDecimal newAmount, Boolean isActive) {
        lineOfCreditCharge.setActive(isActive);
        if (newAmount != null) {
            if (isFlat(lineOfCreditCharge)) {
                lineOfCreditCharge.setAmount(newAmount);
                recalculateOutstanding(lineOfCreditCharge);
            } else if (isPercent(lineOfCreditCharge)) {
                lineOfCreditCharge.setPercentage(newAmount);
                if (lineOfCreditCharge.getAmountPercentageAppliedTo() != null) {
                    applyPercentBase(lineOfCreditCharge, lineOfCreditCharge.getAmountPercentageAppliedTo());
                }
            }
        }
    }

    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment) {
        return pay(c, payment, true);
    }

    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment, boolean advanceRecurringCycle) {
        return pay(c, payment, advanceRecurringCycle, null);
    }

    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment, boolean advanceRecurringCycle,
            SavingsAccountTransaction savingsTransaction) {
        return pay(c, payment, advanceRecurringCycle, savingsTransaction, false);
    }

    /**
     * Pay a charge amount.
     *
     * @param c the LOC charge
     * @param payment the payment amount
     * @param advanceRecurringCycle whether to advance recurring cycle
     * @param savingsTransaction the savings transaction (for journal entry linking)
     * @param skipTaxJournalEntries if true, skip creating tax journal entries (used when savings processor handles them)
     * @return the remaining outstanding amount
     */
    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment, boolean advanceRecurringCycle,
            SavingsAccountTransaction savingsTransaction, boolean skipTaxJournalEntries) {
        require(payment != null && payment.compareTo(BigDecimal.ZERO) > 0, "payment must be > 0");
        BigDecimal toPay = payment.min(c.getAmountOutstanding());
        c.setAmountPaid(ns(c.getAmountPaid()).add(toPay));
        recalculateOutstanding(c);

        // Create journal entries for the tax portion if transaction is provided
        // Skip if skipTaxJournalEntries is true (e.g., when savings processor handles tax journal entries for LOC Activation)
        if (!skipTaxJournalEntries && savingsTransaction != null && c.getTaxAmountDefaulted() != null && c.getTaxAmountDefaulted().compareTo(BigDecimal.ZERO) > 0) {
            createJournalEntriesForChargeTax(c, savingsTransaction, false);
        }

        if (advanceRecurringCycle && c.getAmountOutstanding().compareTo(BigDecimal.ZERO) == 0 && isRecurring(c)) {
            moveToNextCycle(c);
        }
        return c.getAmountOutstanding();

    }

    public BigDecimal waive(LineOfCreditCharge lineOfCreditCharge) {
        BigDecimal outstanding = lineOfCreditCharge.getAmountOutstanding();
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            lineOfCreditCharge.setAmountWaived(ns(lineOfCreditCharge.getAmountWaived()).add(outstanding));
            recalculateOutstanding(lineOfCreditCharge);
            lineOfCreditCharge.setWaived(true);
            if (isRecurring(lineOfCreditCharge)) {
                moveToNextCycle(lineOfCreditCharge);
            }
        }
        return outstanding;
    }

    public void moveToNextCycle(LineOfCreditCharge lineOfCreditCharge) {
        if (!isRecurring(lineOfCreditCharge)) {
            return;
        }
        lineOfCreditCharge.setAmountPaid(BigDecimal.ZERO);
        lineOfCreditCharge.setAmountWaived(BigDecimal.ZERO);
        lineOfCreditCharge.setAmountWrittenOff(BigDecimal.ZERO);
        lineOfCreditCharge.setWaived(false);
        lineOfCreditCharge.setPaid(false);
        lineOfCreditCharge.setAmountOutstanding(lineOfCreditCharge.getAmount());
        if (isMonthly(lineOfCreditCharge)) {
            lineOfCreditCharge.setChargeDueDate(lineOfCreditCharge.getChargeDueDate()
                    .plusMonths(lineOfCreditCharge.getFeeInterval() != null ? lineOfCreditCharge.getFeeInterval() : 1));
        } else if (isAnnual(lineOfCreditCharge)) {
            lineOfCreditCharge.setChargeDueDate(lineOfCreditCharge.getChargeDueDate().plusYears(1));
        } else if (isWeekly(lineOfCreditCharge)) {
            lineOfCreditCharge.setChargeDueDate(lineOfCreditCharge.getChargeDueDate()
                    .plusWeeks(lineOfCreditCharge.getFeeInterval() != null ? lineOfCreditCharge.getFeeInterval() : 1));
        }
    }

    private void recalculateOutstanding(LineOfCreditCharge c) {
        BigDecimal outstanding = ns(c.getAmount()).subtract(ns(c.getAmountPaid())).subtract(ns(c.getAmountWaived()))
                .subtract(ns(c.getAmountWrittenOff()));
        c.setAmountOutstanding(outstanding);
        c.setPaid(outstanding.compareTo(BigDecimal.ZERO) == 0 && !c.isWaived());
    }

    private boolean isRecurring(LineOfCreditCharge c) {
        return isMonthly(c) || isAnnual(c) || isWeekly(c);
    }

    private boolean isMonthly(LineOfCreditCharge c) {
        return ChargeTimeType.fromInt(c.getChargeTime()).isMonthlyFee();
    }

    private boolean isAnnual(LineOfCreditCharge c) {
        return ChargeTimeType.fromInt(c.getChargeTime()).isAnnualFee();
    }

    private boolean isWeekly(LineOfCreditCharge c) {
        return ChargeTimeType.fromInt(c.getChargeTime()).isWeeklyFee();
    }

    private boolean isFlat(LineOfCreditCharge c) {
        return ChargeCalculationType.fromInt(c.getChargeCalculation()) == ChargeCalculationType.FLAT;
    }

    private boolean isPercent(LineOfCreditCharge c) {
        return ChargeCalculationType.fromInt(c.getChargeCalculation()) == ChargeCalculationType.PERCENT_OF_AMOUNT;
    }

    private static void require(boolean condition, String msg) {
        if (!condition) {
            throw new IllegalArgumentException(msg);
        }
    }

    private static BigDecimal ns(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public void unpay(LineOfCreditCharge c, BigDecimal amount, SavingsAccountTransaction reversalTransaction) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal currentPaid = ns(c.getAmountPaid());
        BigDecimal newPaid = currentPaid.subtract(amount);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0) {
            newPaid = BigDecimal.ZERO;
        }
        c.setAmountPaid(newPaid);
        recalculateOutstanding(c);

        // Create reversal journal entries for the tax portion if transaction is provided
        if (reversalTransaction != null && c.getTaxAmountDefaulted() != null && c.getTaxAmountDefaulted().compareTo(BigDecimal.ZERO) > 0) {
            createJournalEntriesForChargeTax(c, reversalTransaction, true);
        }
        // We are not rolling back dueDate if cycle already advanced. Future enhancement could track previous due date.
    }

    private void createJournalEntriesForChargeTax(LineOfCreditCharge charge, SavingsAccountTransaction txn, boolean reversed) {

        Charge chargeDefinition = charge.getChargeDefinition();
        if (chargeDefinition.getTaxGroup() == null) {
            return;
        }

        BigDecimal taxAmount = charge.getTaxAmountDefaulted();
        if (taxAmount == null || taxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LineOfCredit loc = charge.getLineOfCredit();
        if (loc == null || loc.getSettlementSavingsAccount() == null) {
            return;
        }

        List<Map<String, Object>> taxMappings = new ArrayList<>();

        // Get tax GL accounts from the charge's tax group
        chargeDefinition.getTaxGroup().getTaxGroupMappings().forEach(taxMapping -> {
            if (taxMapping.getTaxComponent() != null) {
                Map<String, Object> taxMap = new LinkedHashMap<>();

                // Debit: Tax expense/receivable account (from charge)
                Long debitAccountId = taxMapping.getTaxComponent().getDebitAccount() != null
                        ? taxMapping.getTaxComponent().getDebitAccount().getId()
                        : null;
                // Credit: Tax liability account (from tax component)
                Long creditAccountId = taxMapping.getTaxComponent().getCreditAccount() != null
                        ? taxMapping.getTaxComponent().getCreditAccount().getId()
                        : null;

                if (debitAccountId != null && creditAccountId != null) {
                    taxMap.put("debitAccountId", debitAccountId);
                    taxMap.put("creditAccountId", creditAccountId);
                    taxMap.put("amount", taxAmount);
                    taxMappings.add(taxMap);
                }
            }
        });

        if (!taxMappings.isEmpty()) {
            Map<String, Object> accountingBridgeData = new LinkedHashMap<>();
            accountingBridgeData.put("savingsId", loc.getSettlementSavingsAccount().getId());
            accountingBridgeData.put("savingsTransactionId", txn.getId());
            accountingBridgeData.put("officeId", loc.getSettlementSavingsAccount().officeId());
            accountingBridgeData.put("currencyCode", loc.getCurrency());
            accountingBridgeData.put("date", txn.getTransactionDate());
            accountingBridgeData.put("reversed", reversed);
            accountingBridgeData.put("taxMappings", taxMappings);
            accountingBridgeData.put("cashBasedAccountingEnabled", true);
            accountingBridgeData.put("accrualBasedAccountingEnabled", false);

            journalEntryWritePlatformService.createJournalEntriesForLineOfCredit(accountingBridgeData);
        }
    }

    private void updateTaxAmount(Charge charge, LineOfCreditCharge lineOfCreditCharge, final LocalDate dueDate, final BigDecimal amount) {

        BigDecimal taxAmount = BigDecimal.ZERO;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            LocalDate chargeDate = dueDate != null ? dueDate : DateUtils.getBusinessLocalDate();
            final Set<TaxGroupMappings> taxGroupMappings = charge.getTaxGroup() != null ? charge.getTaxGroup().getTaxGroupMappings()
                    : Collections.emptySet();
            taxAmount = TaxUtils.addTaxToAmount(amount, chargeDate, taxGroupMappings, amount.scale()).subtract(amount);
        }

        lineOfCreditCharge.setTaxAmount(taxAmount);
    }
}
