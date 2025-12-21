package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.event.LineOfCreditApprovedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LineOfCreditApprovedEventForwarder {
    private final BusinessEventNotifierService notifier;

    public LineOfCreditApprovedEventForwarder(BusinessEventNotifierService notifier) {
        this.notifier = notifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApproved(LineOfCreditApprovedBusinessEvent event) {
        notifier.notifyPostBusinessEvent(event);
    }
}