package com.crediblex.fineract.portfolio.savings.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class CredXSavingsAssociatedLoanServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CredXSavingsAssociatedLoanService associatedLoanService;

    @Test
    void recordLoanLinkWritesLoanIdOnly() {
        associatedLoanService.recordLoanLink(100L, 1042L);

        verify(jdbcTemplate).update(contains("associated_loan_id = ?"), eq(1042L), eq(100L));
    }

    @Test
    void recordLoanLinkIgnoresNullIds() {
        associatedLoanService.recordLoanLink(null, 1042L);
        associatedLoanService.recordLoanLink(100L, null);

        verify(jdbcTemplate, never()).update(any(String.class), any(), any());
    }

    @Test
    void recordEmiTransferForRbfSingleInstallment() {
        when(jdbcTemplate.queryForObject(contains("short_name"), eq(String.class), eq(1042L))).thenReturn("RBF");
        when(jdbcTemplate.query(contains("MIN(rs.installment)"), any(ResultSetExtractor.class), eq(500L)))
                .thenReturn(new Integer[] { 3, 3 });

        associatedLoanService.recordEmiTransfer(200L, 1042L, 500L);

        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), eq(3), eq(3), eq(200L));
    }

    @Test
    void recordEmiTransferForRbfInstallmentRange() {
        when(jdbcTemplate.queryForObject(contains("short_name"), eq(String.class), eq(1042L))).thenReturn("RBF");
        when(jdbcTemplate.query(contains("MIN(rs.installment)"), any(ResultSetExtractor.class), eq(500L)))
                .thenReturn(new Integer[] { 2, 3 });

        associatedLoanService.recordEmiTransfer(200L, 1042L, 500L);

        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), eq(2), eq(3), eq(200L));
    }

    @Test
    void recordEmiTransferForRbfOverpaymentOnlyLeavesInstallmentsNull() {
        when(jdbcTemplate.queryForObject(contains("short_name"), eq(String.class), eq(1042L))).thenReturn("RBF");
        when(jdbcTemplate.query(contains("MIN(rs.installment)"), any(ResultSetExtractor.class), eq(500L)))
                .thenReturn(new Integer[] { null, null });

        associatedLoanService.recordEmiTransfer(200L, 1042L, 500L);

        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), isNull(), isNull(), eq(200L));
    }

    @Test
    void recordEmiTransferForNonRbfWritesLoanIdOnly() {
        when(jdbcTemplate.queryForObject(contains("short_name"), eq(String.class), eq(1042L))).thenReturn("LOC");

        associatedLoanService.recordEmiTransfer(200L, 1042L, 500L);

        verify(jdbcTemplate, never()).query(contains("MIN(rs.installment)"), any(ResultSetExtractor.class), any());
        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), isNull(), isNull(), eq(200L));
    }

    @Test
    void recordEmiTransferWhenLoanMissingTreatsAsNonRbf() {
        when(jdbcTemplate.queryForObject(contains("short_name"), eq(String.class), eq(1042L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        associatedLoanService.recordEmiTransfer(200L, 1042L, 500L);

        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), isNull(), isNull(), eq(200L));
    }

    @Test
    void recordEmiTransferWithoutLoanTransactionWritesLoanIdOnly() {
        associatedLoanService.recordEmiTransfer(200L, 1042L, null);

        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(String.class), any());
        verify(jdbcTemplate).update(contains("associated_from_installment"), eq(1042L), isNull(), isNull(), eq(200L));
    }
}
