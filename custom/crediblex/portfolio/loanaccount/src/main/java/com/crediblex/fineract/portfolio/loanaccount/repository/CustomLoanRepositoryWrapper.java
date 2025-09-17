package com.crediblex.fineract.portfolio.loanaccount.repository;

import java.util.Collection;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

@Component
public class CustomLoanRepositoryWrapper {

    @Autowired
    CredibleXLoanRepository credibleXLoanRepository;

    // Repayments Schedule
    public List<Loan> customFindByClientOfficeIdsAndLoanStatus(@Param("officeIds") Collection<Long> officeIds,
            @Param("loanStatuses") Collection<LoanStatus> loanStatuses, @Param("pageSize") int pageSize,
            @Param("maxLoanIdInList") Long maxLoanIdInList) {
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by("id"));
        Page<Loan> result = credibleXLoanRepository.findByClientOfficeIdsAndLoanStatusWithPagination(officeIds, loanStatuses,
                maxLoanIdInList, pageable);
        List<Loan> loans = result.getContent();

        if (loans != null && loans.size() > 0) {
            for (Loan loan : loans) {
                loan.initializeRepaymentSchedule();
            }
        }
        return loans;
    }

    // Repayments Schedule
    public List<Loan> customFindByGroupOfficeIdsAndLoanStatus(@Param("officeIds") Collection<Long> officeIds,
            @Param("loanStatuses") Collection<LoanStatus> loanStatuses, @Param("pageSize") int pageSize,
            @Param("maxLoanIdInList") Long maxLoanIdInList) {
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by("id"));
        Page<Loan> result = credibleXLoanRepository.customFindByGroupOfficeIdsAndLoanStatusWithPagination(officeIds, loanStatuses,
                maxLoanIdInList, pageable);
        List<Loan> loans = result.getContent();
        if (loans != null && loans.size() > 0) {
            for (Loan loan : loans) {
                loan.initializeRepaymentSchedule();
            }
        }
        return loans;
    }
}
