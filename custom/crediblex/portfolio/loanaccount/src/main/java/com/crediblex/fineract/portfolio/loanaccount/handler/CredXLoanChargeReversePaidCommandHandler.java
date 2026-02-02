package com.crediblex.fineract.portfolio.loanaccount.handler;

import com.crediblex.fineract.portfolio.loanaccount.service.CredXLoanChargeWritePlatformService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "LOANCHARGE", action = "REVERSEPAID")
public class CredXLoanChargeReversePaidCommandHandler implements NewCommandSourceHandler {

    private final CredXLoanChargeWritePlatformService writePlatformService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        return writePlatformService.reversePaidLoanCharge(command.getLoanId(), command.entityId(), command);
    }
}
