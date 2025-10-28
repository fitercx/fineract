package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByDTO;

@Getter
@Setter
@RequiredArgsConstructor
public class CustomLoanChargePaidByDTO extends LoanChargePaidByDTO {

    // Tax-related fields
    private String chargeName;
    private Long taxGroupId;
    private String taxGroupName;
    private Long taxGLAccountId;
    private Long incomeGLAccountId;

    private Long creditGLAccountId;
    private Long debitGLAccountId;
    private boolean applicableToFactoRateFeeTaxes;

    // Calculated VAT breakdown
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;

    public void markAsApplicableToFactoRateFeeTaxes() {
        this.applicableToFactoRateFeeTaxes = true;
    }

}
