package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.math.BigDecimal;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless domain service encapsulating LOC charge lifecycle logic.
 */
@Service
@Transactional
public class LineOfCreditChargeDomainService {

    public LineOfCreditCharge create(LineOfCredit loc, Charge definition, BigDecimal overrideAmount) {
        ChargeTimeType timeType = ChargeTimeType.fromInt(definition.getChargeTimeType());
        ChargeCalculationType calcType = ChargeCalculationType.fromInt(definition.getChargeCalculation());
        BigDecimal baseAmount = overrideAmount != null ? overrideAmount : definition.getAmount();
        BigDecimal chargeAmount = baseAmount;

        if (calcType.isPercentageOfAmount()) {
            chargeAmount = loc.getMaximumAmount().multiply(baseAmount).divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
        }

        LineOfCreditCharge c = new LineOfCreditCharge();
        c.setLineOfCredit(loc);
        c.setChargeDefinition(definition);
        c.setPenaltyCharge(definition.isPenalty());
        c.setChargeTime(timeType.getValue());
        c.setChargeCalculation(calcType.getValue());
        c.setActive(true);
        c.setAmountPaid(BigDecimal.ZERO);
        c.setAmountWaived(BigDecimal.ZERO);
        c.setAmountWrittenOff(BigDecimal.ZERO);
        c.setWaived(false);

        if (calcType == ChargeCalculationType.FLAT) {
            c.setAmount(chargeAmount);
            c.setPercentage(null);
            c.setAmountPercentageAppliedTo(null);
        } else { // PERCENT_OF_AMOUNT
            c.setPercentage(baseAmount);
            c.setAmountPercentageAppliedTo(loc.getMaximumAmount());
            c.setAmount(chargeAmount);
        }

        c.setAmountOutstanding(c.getAmount());
        c.setPaid(c.getAmountOutstanding().compareTo(BigDecimal.ZERO) == 0);
        return c;
    }

    public void applyPercentBase(LineOfCreditCharge c, BigDecimal base) {
        if (isPercent(c)) {
            c.setAmountPercentageAppliedTo(base);
            BigDecimal amount = base.multiply(c.getPercentage()).divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
            c.setAmount(amount);
            recalcOutstanding(c);
        }
    }

    public void update(LineOfCreditCharge c, BigDecimal newAmount, Boolean isActive) {
        c.setActive(isActive);
        if (newAmount != null) {
            if (isFlat(c)) {
                c.setAmount(newAmount);
                recalcOutstanding(c);
            } else if (isPercent(c)) {
                c.setPercentage(newAmount);
                if (c.getAmountPercentageAppliedTo() != null) {
                    applyPercentBase(c, c.getAmountPercentageAppliedTo());
                }
            }
        }
    }

    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment) {
        return pay(c, payment, true);
    }

    public BigDecimal pay(LineOfCreditCharge c, BigDecimal payment, boolean advanceRecurringCycle) {
        require(payment != null && payment.compareTo(BigDecimal.ZERO) > 0, "payment must be > 0");
        BigDecimal toPay = payment.min(c.getAmountOutstanding());
        c.setAmountPaid(ns(c.getAmountPaid()).add(toPay));
        recalcOutstanding(c);
        if (advanceRecurringCycle && c.getAmountOutstanding().compareTo(BigDecimal.ZERO) == 0 && isRecurring(c)) {
            moveToNextCycle(c);
        }
        return c.getAmountOutstanding();
    }

    public BigDecimal waive(LineOfCreditCharge c) {
        BigDecimal outstanding = c.getAmountOutstanding();
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            c.setAmountWaived(ns(c.getAmountWaived()).add(outstanding));
            recalcOutstanding(c);
            c.setWaived(true);
            if (isRecurring(c)) {
                moveToNextCycle(c);
            }
        }
        return outstanding;
    }

    public void moveToNextCycle(LineOfCreditCharge c) {
        if (!isRecurring(c)) {
            return;
        }
        c.setAmountPaid(BigDecimal.ZERO);
        c.setAmountWaived(BigDecimal.ZERO);
        c.setAmountWrittenOff(BigDecimal.ZERO);
        c.setWaived(false);
        c.setPaid(false);
        c.setAmountOutstanding(c.getAmount());
        if (isMonthly(c)) {
            c.setChargeDueDate(c.getChargeDueDate().plusMonths(c.getFeeInterval() != null ? c.getFeeInterval() : 1));
        } else if (isAnnual(c)) {
            c.setChargeDueDate(c.getChargeDueDate().plusYears(1));
        } else if (isWeekly(c)) {
            c.setChargeDueDate(c.getChargeDueDate().plusWeeks(c.getFeeInterval() != null ? c.getFeeInterval() : 1));
        }
    }

    private void recalcOutstanding(LineOfCreditCharge c) {
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

    public void unpay(LineOfCreditCharge c, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal currentPaid = ns(c.getAmountPaid());
        BigDecimal newPaid = currentPaid.subtract(amount);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0) {
            newPaid = BigDecimal.ZERO;
        }
        c.setAmountPaid(newPaid);
        recalcOutstanding(c);
        // We are not rolling back dueDate if cycle already advanced. Future enhancement could track previous due date.
    }
}
