package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_line_of_credit_approved_buyers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LineOfCreditApprovedBuyers extends AbstractPersistableCustom<Long> {

    @Column(name = "name")
    private  String name;

    @ManyToOne
    @JoinColumn(name = "line_of_credit_id", referencedColumnName = "id")
    private  LineOfCredit lineOfCredit;
}
