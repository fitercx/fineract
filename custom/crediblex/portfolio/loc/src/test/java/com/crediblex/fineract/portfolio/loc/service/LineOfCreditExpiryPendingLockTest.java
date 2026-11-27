package com.crediblex.fineract.portfolio.loc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.junit.jupiter.api.Test;

class LineOfCreditExpiryPendingLockTest {

    @Test
    void inFlightSlotStaysHeldUntilTheAwaitingCommandIsSaved() {
        assertThat(LineOfCreditExpiryPendingLock.slotIsHeld(null, false)).isTrue();
        assertThat(LineOfCreditExpiryPendingLock.slotIsHeld(15L, true)).isTrue();
        assertThat(LineOfCreditExpiryPendingLock.slotIsHeld(15L, false)).isFalse();
    }

    @Test
    void makerCheckerRollbackKeepsTheSlot() {
        final RollbackTransactionNotApprovedException rollback = new RollbackTransactionNotApprovedException(15L, 5987L);
        assertThat(LineOfCreditExpiryPendingLock.rollbackCause(new IllegalStateException("wrapped", rollback))).isSameAs(rollback);
        assertThat(LineOfCreditExpiryPendingLock.rollbackCause(new IllegalStateException("validation"))).isNull();
    }
}
