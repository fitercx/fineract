package com.crediblex.fineract.portfolio.loanaccount.handler;

import com.google.gson.JsonObject;
import java.util.Collections;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.handler.LoanRepaymentAdjustmentCommandHandler;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@CommandType(entity = "LOAN", action = "ADJUST")
public class CredXLoanRepaymentAdjustmentCommandHandler extends LoanRepaymentAdjustmentCommandHandler {

    public CredXLoanRepaymentAdjustmentCommandHandler(LoanWritePlatformService writePlatformService) {
        super(writePlatformService);
    }

    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        String comment = command.stringValueOfParameterNamed("comment");
        if (comment == null || comment.trim().isEmpty()) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.loan.transaction.adjust.comment.required",
                    "Comment is mandatory to adjust or undo a loan transaction.", "comment");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }
        // Rename 'comment' to 'note' in the command JSON
        JsonObject jsonObject = command.getParsedCommand().getAsJsonObject();
        jsonObject.remove("comment");
        jsonObject.addProperty("note", comment);
        JsonCommand newCommand = JsonCommand.fromExistingCommand(command, jsonObject);
        return super.processCommand(newCommand);
    }
}
