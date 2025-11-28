package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.data.LineOfCreditDTO;

public interface AccountingProcessorForLineOfCredit {

    void createJournalEntriesForLineOfCreditActivationCharge(LineOfCreditDTO lineOfCreditDTO);
}
