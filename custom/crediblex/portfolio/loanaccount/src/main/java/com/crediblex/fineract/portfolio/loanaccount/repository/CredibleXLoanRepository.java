package com.crediblex.fineract.portfolio.loanaccount.repository;

import java.util.Collection;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

@Component
public interface CredibleXLoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    @Query("SELECT loan FROM Loan loan " + "WHERE loan.client.office.id IN :officeIds " + "AND loan.loanStatus IN :loanStatuses "
            + "AND loan.id >= :maxLoanIdInList")
    Page<Loan> findByClientOfficeIdsAndLoanStatusWithPagination(@Param("officeIds") Collection<Long> officeIds,
            @Param("loanStatuses") Collection<LoanStatus> loanStatuses, @Param("maxLoanIdInList") Long maxLoanIdInList, Pageable pageable);

    @Query("SELECT loan FROM Loan loan WHERE loan.group.office.id IN :officeIds AND loan.loanStatus IN :loanStatuses AND loan.id >= :maxLoanIdInList")
    Page<Loan> customFindByGroupOfficeIdsAndLoanStatusWithPagination(@Param("officeIds") Collection<Long> officeIds,
            @Param("loanStatuses") Collection<LoanStatus> loanStatuses, @Param("maxLoanIdInList") Long maxLoanIdInList, Pageable pageable);
}
