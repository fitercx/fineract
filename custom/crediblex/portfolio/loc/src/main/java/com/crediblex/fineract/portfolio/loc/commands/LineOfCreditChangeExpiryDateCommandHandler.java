package com.crediblex.fineract.portfolio.loc.commands;

import com.crediblex.fineract.portfolio.loc.service.LineOfCreditExpiryChangeService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "LINE_OF_CREDIT", action = "CHANGEEXPIRYDATE")
public class LineOfCreditChangeExpiryDateCommandHandler implements NewCommandSourceHandler {

    private final LineOfCreditExpiryChangeService expiryChangeService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        return this.expiryChangeService.apply(command);
    }
}
