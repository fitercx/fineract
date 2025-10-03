package com.crediblex.fineract.portfolio.loanaccount.mapper;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomLoanTermVariationsMapper extends LoanTermVariationsMapper {

    private final LoanLineOfCreditParamsRepository lineOfCreditParamsRepository;

    public CustomLoanTermVariationsMapper(LoanLineOfCreditParamsRepository lineOfCreditParamsRepository) {
        this.lineOfCreditParamsRepository = lineOfCreditParamsRepository;

    }

    @Override
    public LoanApplicationTerms constructLoanApplicationTerms(final ScheduleGeneratorDTO scheduleGeneratorDTO, final Loan loan) {
        LoanApplicationTerms loanTermVariations = super.constructLoanApplicationTerms(scheduleGeneratorDTO, loan);

        Optional<LoanLineOfCreditParams> loanLineOfCreditParamsOptional = lineOfCreditParamsRepository.findByLoanId(loan.getId());

        if (loanLineOfCreditParamsOptional.isPresent()) {
            LoanLineOfCreditParams loanLineOfCreditParams = loanLineOfCreditParamsOptional.get();

            loanTermVariations.setInvoiceAmount(loanLineOfCreditParams.getInvoiceAmount());
            loanTermVariations.setDisapprovedAmount(loanLineOfCreditParams.getDisapprovedAmount());
            loanTermVariations.setApprovedReceivableAmount(loanLineOfCreditParams.getApprovedReceivableAmount());
            loanTermVariations.setAdvancePercentage(loanLineOfCreditParams.getAdvancePercentage());

        }
        return loanTermVariations;
    }

}
