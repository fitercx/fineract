package com.crediblex.fineract.portfolio.savings.handler;

import java.util.Collections;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.handler.UndoTransactionSavingsAccountCommandHandler;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@CommandType(entity = "SAVINGSACCOUNT", action = "UNDOTRANSACTION")
public class CredXUndoTransactionSavingsAccountCommandHandler extends UndoTransactionSavingsAccountCommandHandler {

    private final NoteRepository noteRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final SavingsAccountTransactionRepository savingsAccountTransactionRepository;

    @Autowired
    public CredXUndoTransactionSavingsAccountCommandHandler(SavingsAccountWritePlatformService writePlatformService,
            NoteRepository noteRepository, SavingsAccountRepository savingsAccountRepository,
            SavingsAccountTransactionRepository savingsAccountTransactionRepository) {
        super(writePlatformService);
        this.noteRepository = noteRepository;
        this.savingsAccountRepository = savingsAccountRepository;
        this.savingsAccountTransactionRepository = savingsAccountTransactionRepository;
    }

    @Override
    @Transactional
    public CommandProcessingResult processCommand(final JsonCommand command) {
        String comment = command.stringValueOfParameterNamed("comment");
        if (comment == null || comment.trim().isEmpty()) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.savings.transaction.undo.comment.required",
                    "Comment is mandatory to undo a savings transaction.", "comment");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }
        // Rename comment to note for business logic
        String note = comment;
        // Validate savingsId and transactionId presence before proceeding
        Long savingsId = command.getSavingsId();
        String transactionIdStr = command.getTransactionId();
        if (savingsId == null) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.savings.transaction.undo.savingsId.required",
                    "Savings ID is required.", "savingsId");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }
        if (transactionIdStr == null || transactionIdStr.trim().isEmpty()) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.savings.transaction.undo.transactionId.required",
                    "Transaction ID is required.", "transactionId");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }
        Long transactionEntityId;
        try {
            transactionEntityId = Long.valueOf(transactionIdStr);
        } catch (NumberFormatException e) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.savings.transaction.undo.transactionId.invalid",
                    "Transaction ID must be a numeric identifier.", "transactionId");
            throw new PlatformApiDataValidationException(Collections.singletonList(error), e); // Pass cause exception
        }

        CommandProcessingResult result = super.processCommand(command);
        // Create a note against the savings transaction with the provided note
        SavingsAccount savingsAccount = this.savingsAccountRepository.findById(savingsId)
                .orElseThrow(() -> new org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException(savingsId));
        SavingsAccountTransaction savingsTransaction = this.savingsAccountTransactionRepository.findById(transactionEntityId)
                .orElseThrow(() -> new org.apache.fineract.portfolio.savings.exception.SavingsAccountTransactionNotFoundException(savingsId,
                        transactionEntityId));
        Note noteEntity = Note.savingsTransactionNote(savingsAccount, savingsTransaction, note);
        this.noteRepository.saveAndFlush(noteEntity);
        return result;
    }
}
