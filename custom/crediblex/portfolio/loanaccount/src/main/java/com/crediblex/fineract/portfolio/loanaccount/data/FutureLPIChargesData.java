package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data class to hold future LPI charges calculation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FutureLPIChargesData {
    
    private LocalDate futureDate;
    private BigDecimal totalLPIAmount;
    private BigDecimal totalEMIAmount;
    private String message;
    private Integer overdueInstallments;
    
    public static FutureLPIChargesData empty(LocalDate futureDate) {
        return FutureLPIChargesData.builder()
                .futureDate(futureDate)
                .totalLPIAmount(BigDecimal.ZERO)
                .totalEMIAmount(BigDecimal.ZERO)
                .overdueInstallments(0)
                .message("No overdue charges for the selected date")
                .build();
    }
}