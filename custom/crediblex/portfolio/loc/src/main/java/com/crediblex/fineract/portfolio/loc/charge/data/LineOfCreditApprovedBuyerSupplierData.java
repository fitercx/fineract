package com.crediblex.fineract.portfolio.loc.charge.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LineOfCreditApprovedBuyerSupplierData {

    final Long id;
    final Long buyerSupplierId;
    final String name;
}
