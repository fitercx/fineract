package com.crediblex.fineract.commands.repository;

import io.github.kayr.ezyquery.EzySql;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

import static com.crediblex.fineract.commands.queries.AuditQueries.*;

/**
 * Implementation of LoanChargeWaiverRepository using EzySql
 */
@Component
public class EzySqlLoanChargeWaiverRepository  {
    
    private final EzySql ezySql;
    
    public EzySqlLoanChargeWaiverRepository(EzySql ezySql) {
        this.ezySql = ezySql;
    }
    
    public List<LoanChargeWaiveDetails.Result> fetchLoanChargeWaiverDetails(List<Long> chargeIds) {
        LoanChargeWaiveDetails Q = loanChargeWaiveDetails();
        return ezySql
                    .from(Q)
                    .where(Q.LOAN_CHARGE_ID.in(chargeIds))
                    .list();
            
    }
}