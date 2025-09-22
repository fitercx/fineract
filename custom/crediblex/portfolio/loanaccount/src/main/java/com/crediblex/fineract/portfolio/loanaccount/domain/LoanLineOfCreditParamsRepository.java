package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanLineOfCreditParamsRepository extends JpaRepository<LoanLineOfCreditParams, Long> {

    Optional<LoanLineOfCreditParams> findByLoan(Loan loan);

    @Query("SELECT llp FROM LoanLineOfCreditParams llp WHERE llp.loan.id = :loanId")
    Optional<LoanLineOfCreditParams> findByLoanId(Long loanId);
}
