package com.crediblex.fineract.commands.repository;

import static com.crediblex.fineract.commands.queries.AuditQueries.loanChargeWaiveDetails;

import com.crediblex.fineract.commands.queries.AuditQueries;
import io.github.kayr.ezyquery.EzySql;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implementation of LoanChargeWaiverRepository using EzySql
 */
@Component
public class EzySqlLoanChargeWaiverRepository {

    private final EzySql ezySql;

    public EzySqlLoanChargeWaiverRepository(EzySql ezySql) {
        this.ezySql = ezySql;
    }

    public List<AuditQueries.LoanChargeWaiveDetails.Result> fetchLoanChargeWaiverDetails(List<Long> chargeIds) {
        AuditQueries.LoanChargeWaiveDetails query = loanChargeWaiveDetails();
        return ezySql.from(query).where(query.LOAN_CHARGE_ID.in(chargeIds)).list();

    }
}
