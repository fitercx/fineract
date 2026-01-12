package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.CumulativeFlatInterestLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultLoanScheduleGeneratorFactory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ProgressiveLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleGeneratorFactory extends DefaultLoanScheduleGeneratorFactory {

    private final FactorRateLoanScheduleGenerator factorRateLoanScheduleGenerator;
    private final CumulativeFlatInterestLoanScheduleGenerator cumulativeFlatInterestLoanScheduleGenerator;
    private final CustomCumulativeDecliningBalanceLoanScheduleGenerator customCumulativeDecliningBalanceLoanScheduleGenerator;

    public CustomLoanScheduleGeneratorFactory(ProgressiveLoanScheduleGenerator progressiveLoanScheduleGenerator,
            CumulativeFlatInterestLoanScheduleGenerator cumulativeFlatInterestLoanScheduleGenerator,
            CustomCumulativeDecliningBalanceLoanScheduleGenerator customCumulativeDecliningBalanceLoanScheduleGenerator,
            FactorRateLoanScheduleGenerator factorRateLoanScheduleGenerator) {
        super(progressiveLoanScheduleGenerator, cumulativeFlatInterestLoanScheduleGenerator,
                customCumulativeDecliningBalanceLoanScheduleGenerator);
        this.factorRateLoanScheduleGenerator = factorRateLoanScheduleGenerator;
        this.cumulativeFlatInterestLoanScheduleGenerator = cumulativeFlatInterestLoanScheduleGenerator;
        this.customCumulativeDecliningBalanceLoanScheduleGenerator = customCumulativeDecliningBalanceLoanScheduleGenerator;
    }

    @Override
    public LoanScheduleGenerator create(final LoanScheduleType loanScheduleType, final InterestMethod interestMethod) {
        return switch (loanScheduleType) {
            case CUMULATIVE -> cumulativeLoanScheduleGenerator(interestMethod);
            case PROGRESSIVE -> progressiveLoanScheduleGenerator(interestMethod);
            case FACTOR_RATE -> factorRateLoanScheduleGenerator;
        };
    }

    @Override
    protected LoanScheduleGenerator cumulativeLoanScheduleGenerator(final InterestMethod interestMethod) {
        return switch (interestMethod) {
            case FLAT -> cumulativeFlatInterestLoanScheduleGenerator;
            case DECLINING_BALANCE -> customCumulativeDecliningBalanceLoanScheduleGenerator;
            case INVALID -> null;
        };
    }
}
