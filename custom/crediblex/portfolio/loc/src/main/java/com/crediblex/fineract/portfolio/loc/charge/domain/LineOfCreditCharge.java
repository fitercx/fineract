package com.crediblex.fineract.portfolio.loc.charge.domain;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;

@Getter
@Setter
@Entity
@Table(name = "m_line_of_credit_charge")
public class LineOfCreditCharge extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "line_of_credit_id", referencedColumnName = "id", nullable = false)
    private LineOfCredit lineOfCredit;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", referencedColumnName = "id", nullable = false)
    private Charge chargeDefinition;

    @Column(name = "is_penalty", nullable = false)
    private boolean penaltyCharge;

    @Column(name = "charge_time_enum", nullable = false)
    private Integer chargeTime;

    @Column(name = "charge_due_date")
    private LocalDate chargeDueDate;

    @Column(name = "fee_on_month")
    private Integer feeOnMonth;

    @Column(name = "fee_on_day")
    private Integer feeOnDay;

    @Column(name = "fee_interval")
    private Integer feeInterval;

    @Column(name = "charge_calculation_enum")
    private Integer chargeCalculation;

    @Column(name = "calculation_percentage", precision = 19, scale = 6)
    private BigDecimal percentage;

    @Column(name = "calculation_on_amount", precision = 19, scale = 6)
    private BigDecimal amountPercentageAppliedTo;

    @Column(name = "amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal amount;

    @Column(name = "amount_paid_derived", precision = 19, scale = 6)
    private BigDecimal amountPaid;

    @Column(name = "amount_waived_derived", precision = 19, scale = 6)
    private BigDecimal amountWaived;

    @Column(name = "amount_writtenoff_derived", precision = 19, scale = 6)
    private BigDecimal amountWrittenOff;

    @Column(name = "amount_outstanding_derived", precision = 19, scale = 6, nullable = false)
    private BigDecimal amountOutstanding;

    @Column(name = "is_paid_derived", nullable = false)
    private boolean paid;

    @Column(name = "waived", nullable = false)
    private boolean waived;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "inactivated_on_date")
    private LocalDate inactivationDate;

    @Column(name = "tax_amount", precision = 19, scale = 6)
    private BigDecimal taxAmount;

    public LineOfCreditCharge() {}

    public BigDecimal getTaxAmountDefaulted() {
        return this.taxAmount == null ? BigDecimal.ZERO : this.taxAmount;
    }
}
