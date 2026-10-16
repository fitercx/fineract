package com.crediblex.fineract.portfolio.savings.data;

import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CredXSavingsAssociatedLoanData implements Serializable {

    private final Long loanId;
    private final Long loanClientId;
    private final Integer fromInstallment;
    private final Integer toInstallment;
}
