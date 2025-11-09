package com.crediblex.fineract.portfolio.loanaccount.mapper;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.util.Optional;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomLoanTermVariationMapper extends LoanTermVariationsMapper {

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
            terms.setDisbursedPrincipal(Money.of(loan.getPrincipal().getCurrency(), loan.getProposedPrincipal()));
            terms.setPrincipal(Money.of(loan.getPrincipal().getCurrency(), loan.getProposedPrincipal()));
            terms.setApprovedPrincipal(Money.of(loan.getPrincipal().getCurrency(), loan.getProposedPrincipal()));
            terms.setApprovedReceivableLineAmount(params.get().getApprovedReceivableAmount());
        }
        return terms;
    }
}
