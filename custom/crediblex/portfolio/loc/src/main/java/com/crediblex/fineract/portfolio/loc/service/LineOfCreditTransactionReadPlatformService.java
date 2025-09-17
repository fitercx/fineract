package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditTransactionData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LineOfCreditTransactionReadPlatformService {

    Page<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId, Pageable pageable);

    LineOfCreditTransactionData retrieveTransaction(Long lineOfCreditId, Long transactionId);
}
