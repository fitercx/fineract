package com.crediblex.fineract.portfolio.loanaccount.service;

import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.service.LoanArrearsAgingServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanArrearsAgingImpl extends LoanArrearsAgingServiceImpl {

    public CustomLoanArrearsAgingImpl(JdbcTemplate jdbcTemplate, BusinessEventNotifierService businessEventNotifierService,
            DatabaseSpecificSQLGenerator sqlGenerator) {
        super(jdbcTemplate, businessEventNotifierService, sqlGenerator);
    }

    @Override
    public void updateLoanArrearsAgeingDetails(final Loan loan) {
        if (loan != null) {

            String updateStatement;

            if (loan.isReceivableLocLoan() && (loan.isOverPaid() || loan.isClosedObligationsMet())) {
                // interest will always float for early closure loans with receivable loc, so no need to update arrears
                // aging
                updateStatement = null;
            } else {
                int count = this.jdbcTemplate.queryForObject("select count(mla.loan_id) from m_loan_arrears_aging mla where mla.loan_id =?",
                        Integer.class, loan.getId());
                updateStatement = constructUpdateStatement(loan, count == 0);
            }

            if (updateStatement == null) {
                String deletestatement = "DELETE FROM m_loan_arrears_aging WHERE  loan_id=?";
                this.jdbcTemplate.update(deletestatement, loan.getId()); // NOSONAR
            } else {
                this.jdbcTemplate.update(updateStatement);
            }
        }
    }
}
