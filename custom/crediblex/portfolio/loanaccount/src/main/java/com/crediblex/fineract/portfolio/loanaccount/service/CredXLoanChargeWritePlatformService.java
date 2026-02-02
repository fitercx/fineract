package com.crediblex.fineract.portfolio.loanaccount.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;

/**
 * Extended interface for CredibleX loan charge write operations. Adds custom methods like reversePaidLoanCharge without
 * modifying the base interface.
 */
public interface CredXLoanChargeWritePlatformService extends LoanChargeWritePlatformService {

    /**
     * Reverses a paid loan charge by: 1. Creating a CHARGE_ADJUSTMENT transaction for audit trail (no journal entries)
     * 2. Marking the charge as inactive and resetting paid amounts 3. Updating the loan schedule and summary 4.
     * Creating GL entries when savings deposit is credited (Debit 100062, Credit 210003) 5. Crediting the reversed
     * amount to the linked savings account 6. Creating audit trail
     *
     * @param loanId
     *            The loan account ID
     * @param loanChargeId
     *            The charge ID to reverse
     * @param command
     *            The JSON command containing optional parameters
     * @return CommandProcessingResult with the reversal details
     */
    CommandProcessingResult reversePaidLoanCharge(Long loanId, Long loanChargeId, JsonCommand command);
}
