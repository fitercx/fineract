package com.crediblex.fineract.portfolio.dpdrepayment.domain;

import com.crediblex.fineract.portfolio.dpdrepayment.DpdRepaymentConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "m_product_loan_dpd_repayment_config")
@Getter
@Setter
@NoArgsConstructor
public class DpdRepaymentProductConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_product_id", nullable = false, unique = true)
    private Long loanProductId;

    @Column(name = "enable_dpd_principal_only", nullable = false)
    private boolean enableDpdPrincipalOnly;

    @Column(name = "dpd_threshold", nullable = false)
    private int dpdThreshold = DpdRepaymentConstants.DEFAULT_DPD_THRESHOLD;

    public static DpdRepaymentProductConfig createDefault(final Long loanProductId) {
        final DpdRepaymentProductConfig config = new DpdRepaymentProductConfig();
        config.setLoanProductId(loanProductId);
        config.setEnableDpdPrincipalOnly(false);
        config.setDpdThreshold(DpdRepaymentConstants.DEFAULT_DPD_THRESHOLD);
        return config;
    }
}
