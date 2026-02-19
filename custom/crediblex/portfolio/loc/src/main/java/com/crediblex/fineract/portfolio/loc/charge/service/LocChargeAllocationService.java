package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidBy;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidByRepository;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargeRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocChargeAllocationService {

    private static final String LOC_ACTIVATION_PRODUCT_SHORT_NAME = "LAA";

    private final LineOfCreditRepository lineOfCreditRepository;
    private final LineOfCreditChargeRepository chargeRepository;
    private final LineOfCreditChargePaidByRepository paidByRepository;
    private final LineOfCreditChargeDomainService domainService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void allocateForSavingsWithdrawal(SavingsAccountTransaction txn) {
        // reversal transactions handled separately
        if (txn != null && txn.isReversalTransaction()) {
            return;
        }
        if (txn == null || txn.getId() == null) {
            return;
        }
        if (!txn.isWithdrawal() && !txn.isChargeTransactionAndNotReversed() && !txn.isPayCharge()) {
            return;
        }
        Long savingsAccountId = txn.getSavingsAccount().getId();
        Optional<LineOfCredit> locOpt = lineOfCreditRepository.findBySettlementSavingsAccount_Id(savingsAccountId);
        if (locOpt.isEmpty()) {
            return;
        }
        LineOfCredit loc = locOpt.get();
        BigDecimal remaining = txn.getAmount();
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<LineOfCreditCharge> unpaid = chargeRepository.findUnpaidOrdered(loc.getId());
        if (unpaid.isEmpty()) {
            return;
        }

        for (LineOfCreditCharge charge : unpaid) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal outstanding = charge.getAmountOutstanding();
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // percent-of-amount dynamic calculation: apply base if not yet computed (amount==0)
            if (isPercentOfAmount(charge) && (charge.getAmount() == null || charge.getAmount().compareTo(BigDecimal.ZERO) == 0)) {
                BigDecimal base = loc.getSummary().getConsumedAmount() != null
                        && loc.getSummary().getConsumedAmount().compareTo(BigDecimal.ZERO) > 0 ? loc.getSummary().getConsumedAmount()
                                : (loc.getMaximumAmount() != null ? loc.getMaximumAmount() : outstanding);
                domainService.applyPercentBase(charge, base);
                // refresh outstanding after computation
                outstanding = charge.getAmountOutstanding();
                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
            }
            BigDecimal toApply = remaining.min(outstanding);
            domainService.pay(charge, toApply, false); // do not advance cycle during automatic allocation
            chargeRepository.save(charge);
            paidByRepository.save(LineOfCreditChargePaidBy.of(txn, charge, toApply));
            remaining = remaining.subtract(toApply);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.debug("LOC charge allocation leftover amount {} for txn {} (no further unpaid charges)", remaining, txn.getId());
        }
    }

    @Transactional
    public void reverseForSavingsReversal(SavingsAccountTransaction reversalTxn) {
        if (reversalTxn == null || !reversalTxn.isReversalTransaction()) {
            return;
        }
        Long originalId = reversalTxn.getOriginalTransactionId();
        if (originalId == null) {
            return;
        }
        List<LineOfCreditChargePaidBy> paidList = paidByRepository.findBySavingsTxn(originalId);
        if (paidList.isEmpty()) {
            return;
        }

        // Check if this is an LOC Activation savings product - if so, skip tax journal entries
        // because the savings accounting processor handles tax reversals for LOC Activation
        SavingsAccount savingsAccount = reversalTxn.getSavingsAccount();
        boolean skipTaxJournalEntries = savingsAccount != null && isLOCActivationSavingsProduct(savingsAccount.productId());

        for (LineOfCreditChargePaidBy paid : paidList) {
            LineOfCreditCharge charge = paid.getLineOfCreditCharge();
            domainService.unpay(charge, paid.getAmount(), reversalTxn, skipTaxJournalEntries);
            chargeRepository.save(charge);
            paidByRepository.delete(paid);
        }
        log.debug("Reversed LOC charge allocations for original savings txn {} (reversal txn {}), skipTaxJournalEntries={}", originalId,
                reversalTxn.getId(), skipTaxJournalEntries);
    }

    /**
     * Check if savings product is LOC Activation. Queries product short_name from database to identify LOC Activation
     * products.
     */
    private boolean isLOCActivationSavingsProduct(Long savingsProductId) {
        if (savingsProductId == null) {
            return false;
        }
        try {
            String sql = "SELECT short_name FROM m_savings_product WHERE id = ?";
            String shortName = jdbcTemplate.queryForObject(sql, String.class, savingsProductId);
            return LOC_ACTIVATION_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug("LocChargeAllocationService: Error checking LOC Activation savings product for savingsProductId {}: {}",
                    savingsProductId, e.getMessage());
            return false;
        }
    }

    private boolean isPercentOfAmount(LineOfCreditCharge c) {
        return c.getChargeCalculation() != null
                && c.getChargeCalculation().intValue() == ChargeCalculationType.PERCENT_OF_AMOUNT.getValue();
    }
}
