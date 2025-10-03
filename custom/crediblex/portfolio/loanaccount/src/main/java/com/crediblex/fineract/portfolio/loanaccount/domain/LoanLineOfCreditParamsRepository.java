package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanLineOfCreditParamsRepository extends JpaRepository<LoanLineOfCreditParams, Long> {

    @Query("SELECT llp FROM LoanLineOfCreditParams llp WHERE llp.loan.id = :loanId")
    Optional<LoanLineOfCreditParams> findByLoanId(Long loanId);

    Optional<LocProductType> findLocProductTypeByLoanId(Long loanId);
}
