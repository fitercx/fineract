package com.crediblex.fineract.portfolio.loc.charge.commands;

import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeWriteService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "LOC_CHARGE", action = "DELETE")
public class LocChargeDeleteCommandHandler implements NewCommandSourceHandler {

    private final LineOfCreditChargeWriteService writeService;

    @Override
    @Transactional
    public CommandProcessingResult processCommand(JsonCommand command) {
        return writeService.delete(command.entityId(), command);
    }
}
