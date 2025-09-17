package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.useradministration.domain.AppUser;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LineOfCreditStateChange implements Serializable {

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @ManyToOne
    @JoinColumn(name = "approved_by_user_id", referencedColumnName = "id")
    private AppUser approvedBy;

    @Column(name = "activated_on_date")
    private LocalDate activateOnDate;

    @ManyToOne
    @JoinColumn(name = "activated_by_user_id", referencedColumnName = "id")
    private AppUser activatedBy;

    @Column(name = "closed_on_date")
    private LocalDate closedOnDate;

    @ManyToOne
    @JoinColumn(name = "closed_by_user_id", referencedColumnName = "id")
    private AppUser closedBy;

}
