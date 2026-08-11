package com.crediblex.fineract.portfolio.dpdrepayment.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DpdRepaymentProductConfigRepository extends JpaRepository<DpdRepaymentProductConfig, Long> {

    Optional<DpdRepaymentProductConfig> findByLoanProductId(Long loanProductId);

    void deleteByLoanProductId(Long loanProductId);
}
