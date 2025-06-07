package com.crediblex.fineract.portfolio.loan.repository;

import com.crediblex.fineract.portfolio.loan.queries.LoanQueries;
import com.crediblex.fineract.portfolio.loan.queries.LoanQueries.RapaymentStatusQuery;
import com.crediblex.fineract.portfolio.loan.queries.LoanQueries.RapaymentStatusQuery.Result;
import io.github.kayr.ezyquery.EzySql;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of the CredXLoanTransactionRepository interface.
 * This class handles data access operations related to loan transactions.
 */
@Component
public class CredXLoanTransactionRepository {

    private final EzySql ezySql;

    public CredXLoanTransactionRepository(EzySql ezySql) {
        this.ezySql = ezySql;
    }

    public Result retrieveLoanRepaymentTemplate(Long loanId) {
        RapaymentStatusQuery.Params P = RapaymentStatusQuery.PARAMS;

        return ezySql.from(LoanQueries.rapaymentStatusQuery())
                .setParam(P.LOAN_ID, loanId)
                .setParam(P.NOW, DateUtils.getLocalDateOfTenant())
                .setParam(P.TRANSACTION_TYPES,
                        List.of(LoanTransactionType.REPAYMENT.getValue(), LoanTransactionType.DOWN_PAYMENT.getValue()))
                .one();
    }
}