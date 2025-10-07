package com.crediblex.fineract.portfolio.loc.commands;

import com.crediblex.fineract.portfolio.loc.service.LineOfCreditWritePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@CommandType(entity = "LINE_OF_CREDIT", action = "INCREASE")
public class LineOfCreditIncreaseCreditLimitCommandHandler implements NewCommandSourceHandler {

    private final LineOfCreditWritePlatformService lineOfCreditWritePlatformService;

    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        return lineOfCreditWritePlatformService.increaseCreditLimit(command.entityId(), command);
    }
}
