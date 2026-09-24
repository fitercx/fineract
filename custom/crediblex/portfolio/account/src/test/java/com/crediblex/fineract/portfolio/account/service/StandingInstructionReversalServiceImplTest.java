/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.account.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Covers ATT resolution for SI history rows where account_transfer_transaction_id is NULL because each SI job
 * execution creates a new account_transfer_details row (template details id does not match the ATT).
 */
class StandingInstructionReversalServiceImplTest {

    private JdbcTemplate jdbcTemplate;
    private AccountTransferRepository accountTransferRepository;
    private LoanAccountDomainService loanAccountDomainService;
    private SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private PlatformSecurityContext context;
    private BusinessEventNotifierService businessEventNotifierService;
    private LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private StandingInstructionReversalServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        accountTransferRepository = mock(AccountTransferRepository.class);
        loanAccountDomainService = mock(LoanAccountDomainService.class);
        savingsAccountWritePlatformService = mock(SavingsAccountWritePlatformService.class);
        context = mock(PlatformSecurityContext.class);
        businessEventNotifierService = mock(BusinessEventNotifierService.class);
        loanStatusWebhookPublisher = mock(LoanStatusWebhookPublisher.class);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(context.authenticatedUser()).thenReturn(user);

        service = new StandingInstructionReversalServiceImpl(jdbcTemplate, accountTransferRepository, loanAccountDomainService,
                savingsAccountWritePlatformService, context, businessEventNotifierService, loanStatusWebhookPublisher);
    }

    @Test
    void reverseExecution_resolvesAttByAccountsWhenDetailsIdDoesNotMatch() throws Exception {
        Long historyId = 10828L;
        Long fromSavingsId = 16803L;
        Long toLoanId = 16188L;
        BigDecimal amount = new BigDecimal("18242.12");
        LocalDate executionDate = LocalDate.of(2026, 9, 3);

        stubHistoryRow(historyId, amount, executionDate, null, 9999L, fromSavingsId, toLoanId);

        when(accountTransferRepository.findByDetailsAndDateAndAmount(eq(9999L), eq(executionDate), eq(amount)))
                .thenReturn(Collections.emptyList());

        AccountTransferTransaction att = mockSuccessfulAtt(fromSavingsId, toLoanId, amount, executionDate);
        when(accountTransferRepository.findByFromSavingsToLoanAndDateAndAmount(eq(fromSavingsId), eq(toLoanId), eq(executionDate),
                eq(amount))).thenReturn(List.of(att));

        // Blank note fails after ATT resolution — proves account-based fallback found the transfer
        JsonCommand command = mock(JsonCommand.class);
        when(command.stringValueOfParameterNamed("note")).thenReturn(" ");

        assertThatThrownBy(() -> service.reverseExecution(historyId, command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("note");

        verify(accountTransferRepository).findByDetailsAndDateAndAmount(9999L, executionDate, amount);
        verify(accountTransferRepository).findByFromSavingsToLoanAndDateAndAmount(fromSavingsId, toLoanId, executionDate, amount);
        verify(loanAccountDomainService, never()).reverseTransfer(any());
    }

    @Test
    void reverseExecution_failsWhenNoAttMatchesEvenByAccounts() throws Exception {
        Long historyId = 10828L;
        Long fromSavingsId = 16803L;
        Long toLoanId = 16188L;
        BigDecimal amount = new BigDecimal("18242.12");
        LocalDate executionDate = LocalDate.of(2026, 9, 3);

        stubHistoryRow(historyId, amount, executionDate, null, 9999L, fromSavingsId, toLoanId);

        when(accountTransferRepository.findByDetailsAndDateAndAmount(anyLong(), any(), any())).thenReturn(Collections.emptyList());
        when(accountTransferRepository.findByFromSavingsToLoanAndDateAndAmount(anyLong(), anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        JsonCommand command = mock(JsonCommand.class);
        when(command.stringValueOfParameterNamed("note")).thenReturn("test reverse");

        assertThatThrownBy(() -> service.reverseExecution(historyId, command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("No account transfer transaction found");

        verify(loanAccountDomainService, never()).reverseTransfer(any());
    }

    private void stubHistoryRow(Long historyId, BigDecimal amount, LocalDate executionDate, Long attId, Long detailsId,
            Long fromSavingsId, Long toLoanId) throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Object>>any(), eq(historyId))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(historyId);
            when(rs.getLong("standing_instruction_id")).thenReturn(8115L);
            when(rs.getString("status")).thenReturn("success");
            when(rs.getBigDecimal("amount")).thenReturn(amount);
            when(rs.getDate("execution_date")).thenReturn(Date.valueOf(executionDate));
            when(rs.getObject("account_transfer_transaction_id", Long.class)).thenReturn(attId);
            when(rs.getBoolean("is_reversed")).thenReturn(false);
            when(rs.getLong("account_transfer_details_id")).thenReturn(detailsId);
            when(rs.getObject("from_savings_account_id", Long.class)).thenReturn(fromSavingsId);
            when(rs.getObject("to_loan_account_id", Long.class)).thenReturn(toLoanId);
            return mapper.mapRow(rs, 0);
        });
    }

    private AccountTransferTransaction mockSuccessfulAtt(Long fromSavingsId, Long toLoanId, BigDecimal amount, LocalDate date) {
        AccountTransferTransaction att = mock(AccountTransferTransaction.class);
        AccountTransferDetails details = mock(AccountTransferDetails.class);
        SavingsAccount fromSavings = mock(SavingsAccount.class);
        Loan toLoan = mock(Loan.class);
        LoanTransaction loanTxn = mock(LoanTransaction.class);
        SavingsAccountTransaction savingsTxn = mock(SavingsAccountTransaction.class);

        when(fromSavings.getId()).thenReturn(fromSavingsId);
        when(toLoan.getId()).thenReturn(toLoanId);
        when(toLoan.isClosedWrittenOff()).thenReturn(false);
        when(toLoan.getLoanSubStatus()).thenReturn(null);
        when(toLoan.getLoanTransactions()).thenReturn(List.of(loanTxn));
        when(toLoan.hasCustomStatus()).thenReturn(false);

        when(loanTxn.getId()).thenReturn(5001L);
        when(loanTxn.isReversed()).thenReturn(false);
        when(loanTxn.getTransactionDate()).thenReturn(date);
        when(loanTxn.isRepaymentLikeType()).thenReturn(true);
        when(loanTxn.isChargePayment()).thenReturn(false);

        when(savingsTxn.getId()).thenReturn(6001L);

        when(details.fromSavingsAccount()).thenReturn(fromSavings);
        when(details.toLoanAccount()).thenReturn(toLoan);
        when(att.accountTransferDetails()).thenReturn(details);
        when(att.isReversed()).thenReturn(false);
        when(att.getToLoanTransaction()).thenReturn(loanTxn);
        when(att.getFromTransaction()).thenReturn(savingsTxn);
        when(att.getAmount()).thenReturn(amount);
        when(att.getDate()).thenReturn(date);

        return att;
    }
}
