package com.crediblex.fineract.accounting.journalentry.data;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.accounting.journalentry.data.ChargePaymentDTO;

@Setter
@Getter
public class CustomChargePaymentDTO extends ChargePaymentDTO {

    private Long taxGroupId;
    private String taxGroupName;
    private BigDecimal taxRate;
    private Long taxGLAccountId;
    private Long incomeGLAccountId;

    private Long creditGLAccountId;
    private Long debitGLAccountId;
    private boolean applicableToFactoRateFeeTaxes;

    // Calculated fields
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;

    public CustomChargePaymentDTO(Long chargeId, BigDecimal amount, Long loanChargeId) {
        super(chargeId, amount, loanChargeId);
    }

    public boolean hasTax() {
        return taxGroupId != null && taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0 && taxGLAccountId != null;
    }

}
