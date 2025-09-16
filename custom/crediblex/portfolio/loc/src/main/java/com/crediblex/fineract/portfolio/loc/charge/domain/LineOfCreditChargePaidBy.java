package com.crediblex.fineract.portfolio.loc.charge.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;

@Entity
@Table(name = "m_line_of_credit_charge_paid_by")
public class LineOfCreditChargePaidBy extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "savings_account_transaction_id", nullable = false)
    private SavingsAccountTransaction savingsAccountTransaction;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "line_of_credit_charge_id", nullable = false)
    private LineOfCreditCharge lineOfCreditCharge;

    @Column(name = "amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal amount;

    protected LineOfCreditChargePaidBy() {}

    private LineOfCreditChargePaidBy(SavingsAccountTransaction txn, LineOfCreditCharge charge, BigDecimal amount) {
        this.savingsAccountTransaction = txn;
        this.lineOfCreditCharge = charge;
        this.amount = amount;
    }

    public static LineOfCreditChargePaidBy of(SavingsAccountTransaction txn, LineOfCreditCharge charge, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        return new LineOfCreditChargePaidBy(txn, charge, amount);
    }

    public BigDecimal getAmount() { return amount; }
    public LineOfCreditCharge getLineOfCreditCharge() { return lineOfCreditCharge; }
    public SavingsAccountTransaction getSavingsAccountTransaction() { return savingsAccountTransaction; }
}
