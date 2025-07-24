package com.crediblex.fineract.portfolio.loanaccount.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByDTO;

import java.math.BigDecimal;

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

    // Calculated VAT breakdown
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;

    public boolean hasTax() {
        return taxGroupId != null && taxGLAccountId != null;
    }
}
