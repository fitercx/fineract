package com.crediblex.fineract.accounting.journalentry;

import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.infrastructure.event.business.domain.journalentry.JournalEntryBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.NoExternalEvent;

public class SavingsJournalEntryCreatedBusinessEvent extends JournalEntryBusinessEvent implements NoExternalEvent {

    private static final String TYPE = "SavingsJournalEntryCreatedBusinessEvent";

    public SavingsJournalEntryCreatedBusinessEvent(JournalEntry value) {
        super(value);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Long getAggregateRootId() {
        return get().getSavingsTransactionId();
    }
}
