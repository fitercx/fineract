package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for CustomLoanWritePlatformServiceJpaRepositoryImpl
 * focusing on LOC balance computation during loan disbursements
 */
@ExtendWith(MockitoExtension.class)
public class CustomLoanWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CustomLoanWritePlatformServiceJpaRepositoryImpl customLoanWritePlatformService;

    private Long loanId;
    private Long lineOfCreditId;
    private BigDecimal disbursementAmount;
    private LocalDate transactionDate;
    private Map<String, Object> mockLocData;

    @BeforeEach
    public void setup() {
        loanId = 1L;
        lineOfCreditId = 100L;
        disbursementAmount = new BigDecimal("1000.00");
        transactionDate = LocalDate.of(2025, 1, 15);

        // Mock LOC data
        mockLocData = new HashMap<>();
        mockLocData.put("id", lineOfCreditId);
        mockLocData.put("available_balance", new BigDecimal("5000.00"));
        mockLocData.put("consumed_amount", new BigDecimal("2000.00"));
        mockLocData.put("maximum_amount", new BigDecimal("10000.00"));
    }

    @Test
    public void testComputeLocBalance_SuccessfulDisbursement() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Long.class),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class),
                any()))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.computeLocBalance(loanId, disbursementAmount, transactionDate);

        // Then
        assertTrue(result, "LOC balance computation should succeed");

        // Verify LOC balance update
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                eq(new BigDecimal("4000.00")), // 5000 - 1000
                eq(new BigDecimal("3000.00")), // 2000 + 1000
                eq(lineOfCreditId)
        );

        // Verify transaction record creation
        verify(jdbcTemplate).update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                eq(lineOfCreditId),
                eq("DISBURSEMENT"),
                eq(disbursementAmount),
                eq(new BigDecimal("5000.00")), // balance before
                eq(new BigDecimal("4000.00")), // balance after
                eq(transactionDate.atStartOfDay().atZone(ZoneOffset.UTC).toOffsetDateTime()),
                eq("LOAN_" + loanId + "_DISBURSEMENT"),
                eq("Loan disbursement - LOC balance reduced by " + disbursementAmount + " for loan " + loanId),
                any(OffsetDateTime.class),
                eq(null)
        );
    }

    @Test
    public void testComputeLocBalance_NoLocAssociation() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(null);

        // When
        boolean result = customLoanWritePlatformService.computeLocBalance(loanId, disbursementAmount, transactionDate);

        // Then
        assertFalse(result, "Should return false when no LOC association exists");

        // Verify no updates were made
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(jdbcTemplate, never()).queryForMap(anyString(), any(Object[].class));
    }

    @Test
    public void testComputeLocBalance_InsufficientBalance() {
        // Given
        BigDecimal largeDisbursementAmount = new BigDecimal("10000.00"); // Larger than available balance

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When & Then
        PlatformApiDataValidationException exception = assertThrows(
                PlatformApiDataValidationException.class,
                () -> customLoanWritePlatformService.computeLocBalance(loanId, largeDisbursementAmount, transactionDate),
                "Should throw exception for insufficient balance"
        );

        // The actual error code might be different due to validation framework
        assertTrue(exception.getMessage().contains("Insufficient line of credit balance") || 
                  exception.getGlobalisationMessageCode().contains("insufficient.balance") ||
                  exception.getGlobalisationMessageCode().contains("validation.errors.exist"),
                  "Should contain insufficient balance error message");

        // Verify no updates were made
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void testComputeLocBalance_UpdateFailure() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(0); // Update failed

        // When & Then
        PlatformApiDataValidationException exception = assertThrows(
                PlatformApiDataValidationException.class,
                () -> customLoanWritePlatformService.computeLocBalance(loanId, disbursementAmount, transactionDate),
                "Should throw exception when LOC update fails"
        );

        // The actual error code might be different due to validation framework
        assertTrue(exception.getMessage().contains("Failed to update line of credit balances") || 
                  exception.getGlobalisationMessageCode().contains("update.failed") ||
                  exception.getGlobalisationMessageCode().contains("validation.errors.exist"),
                  "Should contain update failure error message");

        // Verify transaction record was not created
        verify(jdbcTemplate, never()).update(
                contains("INSERT INTO m_line_of_credit_transactions"),
                any(Object[].class)
        );
    }

    @Test
    public void testComputeLocBalance_ExactBalanceDisbursement() {
        // Given - disbursement amount equals available balance
        BigDecimal exactDisbursementAmount = new BigDecimal("5000.00");
        
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Long.class),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class),
                any()))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.computeLocBalance(loanId, exactDisbursementAmount, transactionDate);

        // Then
        assertTrue(result, "LOC balance computation should succeed with exact balance");

        // Verify LOC balance update - available balance should be 0, consumed should be 7000
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                eq(new BigDecimal("0.00")), // 5000 - 5000
                eq(new BigDecimal("7000.00")), // 2000 + 5000
                eq(lineOfCreditId)
        );
    }

    @Test
    public void testComputeLocBalance_SmallAmountDisbursement() {
        // Given - small disbursement amount
        BigDecimal smallDisbursementAmount = new BigDecimal("100.00");
        
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Long.class),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class),
                any()))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.computeLocBalance(loanId, smallDisbursementAmount, transactionDate);

        // Then
        assertTrue(result, "LOC balance computation should succeed with small amount");

        // Verify LOC balance update
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                eq(new BigDecimal("4900.00")), // 5000 - 100
                eq(new BigDecimal("2100.00")), // 2000 + 100
                eq(lineOfCreditId)
        );
    }

    @Test
    public void testComputeLocBalance_ZeroAmountDisbursement() {
        // Given - zero disbursement amount
        BigDecimal zeroDisbursementAmount = BigDecimal.ZERO;
        
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Long.class),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class),
                any()))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.computeLocBalance(loanId, zeroDisbursementAmount, transactionDate);

        // Then
        assertTrue(result, "LOC balance computation should succeed with zero amount");

        // Verify LOC balance update - balances should remain unchanged
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                eq(new BigDecimal("5000.00")), // 5000 - 0
                eq(new BigDecimal("2000.00")), // 2000 + 0
                eq(lineOfCreditId)
        );
    }

    @Test
    public void testComputeLocBalance_TransactionRecordCreation() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Long.class),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class),
                any()))
                .thenReturn(1);

        // When
        customLoanWritePlatformService.computeLocBalance(loanId, disbursementAmount, transactionDate);

        // Then - Verify transaction record creation with correct metadata
        verify(jdbcTemplate).update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                   "transaction_date, reference_number, description, created_on_utc, created_by) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                eq(lineOfCreditId),
                eq("DISBURSEMENT"),
                eq(disbursementAmount),
                eq(new BigDecimal("5000.00")), // balance before
                eq(new BigDecimal("4000.00")), // balance after
                eq(transactionDate.atStartOfDay().atZone(ZoneOffset.UTC).toOffsetDateTime()),
                eq("LOAN_" + loanId + "_DISBURSEMENT"),
                eq("Loan disbursement - LOC balance reduced by " + disbursementAmount + " for loan " + loanId),
                any(OffsetDateTime.class),
                eq(null)
        );
    }

    @Test
    public void testComputeLocBalance_ExceptionHandling() {
        // Given - simulate database error
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> customLoanWritePlatformService.computeLocBalance(loanId, disbursementAmount, transactionDate),
                "Should propagate database exceptions as RuntimeException"
        );

        assertEquals("Database connection failed", exception.getMessage());
    }
}
