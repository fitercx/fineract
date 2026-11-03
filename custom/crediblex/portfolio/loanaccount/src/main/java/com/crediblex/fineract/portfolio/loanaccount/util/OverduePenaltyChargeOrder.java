package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;

/**
 * Chooses the next unpaid LPI charge when more than one installment is overdue.
 * <p>
 * The repayment strategy already pays penalty, then interest, then principal on the oldest installment before it
 * touches the next one. The charge list used to ignore that and pay the earliest calendar date, so a day that both
 * installments share was taken from the later installment before the oldest installment's later days were finished.
 * This keeps every unpaid charge of the oldest installment ahead of the next installment. One installment, or charges
 * that are not overdue LPI, return {@code null} so the caller keeps date order.
 */
public final class OverduePenaltyChargeOrder {

    private OverduePenaltyChargeOrder() {}

    public static LoanCharge nextUnpaid(final Set<LoanCharge> charges, final MonetaryCurrency currency) {
        if (charges == null || charges.isEmpty() || currency == null || distinctOwningInstallments(charges, currency) < 2) {
            return null;
        }
        LoanCharge selected = null;
        Integer selectedInstallment = null;
        for (final LoanCharge charge : charges) {
            final Integer installmentNumber = unpaidOwnerInstallment(charge, currency);
            if (installmentNumber == null) {
                continue;
            }
            if (selected == null || comesBefore(charge, installmentNumber, selected, selectedInstallment)) {
                selected = charge;
                selectedInstallment = installmentNumber;
            }
        }
        return selected;
    }

    private static int distinctOwningInstallments(final Set<LoanCharge> charges, final MonetaryCurrency currency) {
        final java.util.Set<Integer> owners = new java.util.HashSet<>();
        for (final LoanCharge charge : charges) {
            final Integer installmentNumber = unpaidOwnerInstallment(charge, currency);
            if (installmentNumber != null) {
                owners.add(installmentNumber);
            }
        }
        return owners.size();
    }

    private static Integer unpaidOwnerInstallment(final LoanCharge charge, final MonetaryCurrency currency) {
        if (charge == null || !charge.isOverdueInstallmentCharge() || charge.isDueAtDisbursement() || charge.isWaived()
                || !charge.isActive()) {
            return null;
        }
        if (charge.getAmountOutstanding(currency) == null || !charge.getAmountOutstanding(currency).isGreaterThanZero()) {
            return null;
        }
        final LoanOverdueInstallmentCharge link = charge.getOverdueInstallmentCharge();
        if (link == null || link.getInstallment() == null) {
            return null;
        }
        return link.getInstallment().getInstallmentNumber();
    }

    private static boolean comesBefore(final LoanCharge candidate, final Integer candidateInstallment, final LoanCharge current,
            final Integer currentInstallment) {
        final int installmentOrder = candidateInstallment.compareTo(currentInstallment);
        if (installmentOrder != 0) {
            return installmentOrder < 0;
        }
        final LocalDate candidateDate = candidate.getDueLocalDate();
        final LocalDate currentDate = current.getDueLocalDate();
        if (candidateDate == null) {
            return false;
        }
        if (currentDate == null || candidateDate.isBefore(currentDate)) {
            return true;
        }
        if (candidateDate.isAfter(currentDate)) {
            return false;
        }
        final Long candidateId = candidate.getId();
        final Long currentId = current.getId();
        if (candidateId == null) {
            return false;
        }
        return currentId == null || candidateId < currentId;
    }
}
