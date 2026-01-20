package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_line_of_credit_approved_buyers")
@Getter
@Setter
@NoArgsConstructor
public class LineOfCreditApprovedBuyers extends AbstractPersistableCustom<Long> {

    @Column(name = "name")
    private String name;

    @Column(name = "credit_limit", nullable = false)
    private BigDecimal creditLimit;

    @ManyToOne
    @JoinColumn(name = "line_of_credit_id", referencedColumnName = "id")
    private LineOfCredit lineOfCredit;

    public LineOfCreditApprovedBuyers(String name, LineOfCredit lineOfCredit) {
        this.name = name;
        this.lineOfCredit = lineOfCredit;
        this.creditLimit = BigDecimal.ZERO;
    }

    public LineOfCreditApprovedBuyers(String name, BigDecimal creditLimit, LineOfCredit lineOfCredit) {
        this.name = name;
        this.creditLimit = creditLimit != null ? creditLimit : BigDecimal.ZERO;
        this.lineOfCredit = lineOfCredit;
    }
}
