package com.crediblex.fineract.portfolio.loanaccount.mapper;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomLoanTermVariationMapper extends LoanTermVariationsMapper {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanTermVariationMapper.class);

    LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public CustomLoanTermVariationMapper(LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public LoanApplicationTerms constructLoanApplicationTerms(
            final org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO scheduleGeneratorDTO,
            final org.apache.fineract.portfolio.loanaccount.domain.Loan loan) {
        LoanApplicationTerms terms = super.constructLoanApplicationTerms(scheduleGeneratorDTO, loan);

        Optional<LoanLineOfCreditParams> params = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        terms.setIsPayableLineOfCredit(params.isPresent() && params.get().getLineOfCredit().getProductType().isPayable());
        terms.setIsReceivableLineOfCredit(params.isPresent() && params.get().getLineOfCredit().getProductType().isReceivable());
        loan.setReceivableLocLoan(params.isPresent() && params.get().getLineOfCredit().getProductType().isReceivable());

        if (terms.getIsReceivableLineOfCredit()) {
            // For LOC Receivable loans, use the repayment schedule detail principal for schedule generation.
            // This ensures that when the principal is temporarily set to proposed principal (amountAfterAdvance)
            // during approval, the schedule is generated with the correct principal context.
            // The repayment schedule detail principal reflects the current principal context (either approved
            // principal or proposed principal depending on when this is called).
            BigDecimal principalForSchedule = loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount();
            BigDecimal approvedPrincipal = loan.getApprovedPrincipal();
            log.info("LOC Receivable loan {}: Constructing terms with principal={}, approvedPrincipal={}, amountAfterAdvance={}",
                    loan.getId(), principalForSchedule, approvedPrincipal, params.get().getAmountAfterAdvance());
            terms.setDisbursedPrincipal(Money.of(loan.getPrincipal().getCurrency(), approvedPrincipal));
            terms.setPrincipal(Money.of(loan.getPrincipal().getCurrency(), principalForSchedule));
            terms.setApprovedPrincipal(Money.of(loan.getPrincipal().getCurrency(), approvedPrincipal));
            terms.setAmountAfterAdvance(params.get().getAmountAfterAdvance());
        }
        return terms;
    }
}
