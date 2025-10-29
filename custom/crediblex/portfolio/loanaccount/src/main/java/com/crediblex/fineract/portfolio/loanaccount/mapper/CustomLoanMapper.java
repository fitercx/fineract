package com.crediblex.fineract.portfolio.loanaccount.mapper;

import java.math.MathContext;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanMapper extends LoanMapper {

    private final LoanTermVariationsMapper loanTermVariationsMapper;

    public CustomLoanMapper(LoanTermVariationsMapper loanTermVariationsMapper) {
        super(loanTermVariationsMapper);
        this.loanTermVariationsMapper = loanTermVariationsMapper;
    }

    @Override
    public LoanScheduleModel regenerateScheduleModel(final ScheduleGeneratorDTO scheduleGeneratorDTO, final Loan loan) {
        final MathContext mc = MoneyHelper.getMathContext();

        final LoanApplicationTerms loanApplicationTerms = loanTermVariationsMapper.constructLoanApplicationTerms(scheduleGeneratorDTO,
                loan);
        LoanScheduleGenerator loanScheduleGenerator;
        if (loanApplicationTerms.isEqualAmortization()) {
            if (loanApplicationTerms.getInterestMethod().isDecliningBalance()) {
                final LoanScheduleGenerator decliningLoanScheduleGenerator = scheduleGeneratorDTO.getLoanScheduleFactory()
                        .create(loanApplicationTerms.getLoanScheduleType(), InterestMethod.DECLINING_BALANCE);
                Set<LoanCharge> loanCharges = loan.getActiveCharges();
                LoanScheduleModel loanSchedule = decliningLoanScheduleGenerator.generate(mc, loanApplicationTerms, loanCharges,
                        scheduleGeneratorDTO.getHolidayDetailDTO());

                loanApplicationTerms
                        .updateTotalInterestDue(Money.of(loanApplicationTerms.getCurrency(), loanSchedule.getTotalInterestCharged()));

            }
            loanScheduleGenerator = scheduleGeneratorDTO.getLoanScheduleFactory().create(loanApplicationTerms.getLoanScheduleType(),
                    InterestMethod.FLAT);
        } else {
            loanScheduleGenerator = scheduleGeneratorDTO.getLoanScheduleFactory().create(loanApplicationTerms.getLoanScheduleType(),
                    loanApplicationTerms.getInterestMethod());
        }

        return loanScheduleGenerator.generate(mc, loanApplicationTerms, loan.getActiveCharges(),
                scheduleGeneratorDTO.getHolidayDetailDTO());
    }
}
