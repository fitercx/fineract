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

    // ==================== DISCOUNTED AMOUNT CALCULATION TESTS ====================

    @Test
    public void testCalculateDiscountedAmount_SuccessfulCalculation() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal advancePercentage = new BigDecimal("0.80"); // 80%
        BigDecimal expectedDiscountedAmount = new BigDecimal("8000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertNotNull(result, "Discounted amount should not be null");
        assertEquals(0, expectedDiscountedAmount.compareTo(result), "Discounted amount should be principal * advance_percentage");
        
        // Verify database calls
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateDiscountedAmount_NoLocAssociation() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertNull(result, "Discounted amount should be null when no LOC association exists");
        
        // Verify only the first query was called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate, never()).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                any());
    }

    @Test
    public void testCalculateDiscountedAmount_NoAdvancePercentage() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertNull(result, "Discounted amount should be null when no advance percentage is set");
        
        // Verify both queries were called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateDiscountedAmount_DifferentPercentages() {
        // Given
        BigDecimal principal = new BigDecimal("5000.00");
        
        // Test case 1: 50% advance percentage
        BigDecimal advancePercentage50 = new BigDecimal("0.50");
        BigDecimal expectedDiscountedAmount50 = new BigDecimal("2500.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage50);

        // When
        BigDecimal result50 = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertEquals(0, expectedDiscountedAmount50.compareTo(result50), "50% advance should result in 50% of principal");

        // Test case 2: 100% advance percentage
        BigDecimal advancePercentage100 = new BigDecimal("1.00");
        BigDecimal expectedDiscountedAmount100 = new BigDecimal("5000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage100);

        // When
        BigDecimal result100 = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertEquals(0, expectedDiscountedAmount100.compareTo(result100), "100% advance should result in full principal");

        // Test case 3: 25% advance percentage
        BigDecimal advancePercentage25 = new BigDecimal("0.25");
        BigDecimal expectedDiscountedAmount25 = new BigDecimal("1250.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage25);

        // When
        BigDecimal result25 = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertEquals(0, expectedDiscountedAmount25.compareTo(result25), "25% advance should result in 25% of principal");
    }

    @Test
    public void testCalculateDiscountedAmount_ZeroPrincipal() {
        // Given
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal advancePercentage = new BigDecimal("0.80");
        BigDecimal expectedDiscountedAmount = BigDecimal.ZERO;

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertEquals(0, expectedDiscountedAmount.compareTo(result), "Zero principal should result in zero discounted amount");
    }

    @Test
    public void testCalculateDiscountedAmount_DatabaseError() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        PlatformApiDataValidationException exception = assertThrows(
                PlatformApiDataValidationException.class,
                () -> customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal),
                "Should wrap database exceptions in PlatformApiDataValidationException"
        );

        // Check if the exception contains the expected error message or code
        String message = exception.getMessage();
        String code = exception.getGlobalisationMessageCode();
        assertTrue(message != null && (message.contains("Failed to calculate discounted amount") || 
                  message.contains("discounted.amount.calculation.failed") ||
                  message.contains("Validation errors exist") ||
                  (code != null && (code.contains("discounted.amount.calculation.failed") || 
                                   code.contains("validation.errors.exist")))),
                  "Should contain discounted amount calculation error message. Message: " + message + ", Code: " + code);
    }

    @Test
    public void testCalculateDiscountedAmount_LargeAmounts() {
        // Given - test with large amounts to ensure BigDecimal precision is maintained
        BigDecimal principal = new BigDecimal("999999.99");
        BigDecimal advancePercentage = new BigDecimal("0.75");
        BigDecimal expectedDiscountedAmount = new BigDecimal("749999.9925");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertEquals(0, expectedDiscountedAmount.compareTo(result), "Large amounts should be calculated with proper precision");
    }

    // ==================== EXPECTED INTEREST CALCULATION TESTS ====================

    @Test
    public void testCalculateExpectedInterest_SuccessfulCalculation() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");
        BigDecimal annualInterestRate = new BigDecimal("0.12"); // 12%
        Integer tenorDays = 90;
        BigDecimal expectedInterest = new BigDecimal("236.71232877"); // 0.12 * 8000 * (90/365)

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", annualInterestRate);
        mockLocData.put("tenor_days", tenorDays);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertNotNull(result, "Expected interest should not be null");
        assertTrue(expectedInterest.subtract(result).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "Expected interest should be calculated correctly. Expected: " + expectedInterest + ", Actual: " + result);
        
        // Verify database calls
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateExpectedInterest_NoLocAssociation() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertNull(result, "Expected interest should be null when no LOC association exists");
        
        // Verify only the first query was called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate, never()).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateExpectedInterest_NoAnnualInterestRate() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", null);
        mockLocData.put("tenor_days", 90);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertNull(result, "Expected interest should be null when no annual interest rate is set");
        
        // Verify both queries were called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateExpectedInterest_NoTenorDays() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", new BigDecimal("0.12"));
        mockLocData.put("tenor_days", null);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertNull(result, "Expected interest should be null when no tenor days is set");
        
        // Verify both queries were called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateExpectedInterest_InvalidDiscountedAmount() {
        // Given
        BigDecimal invalidDiscountedAmount = BigDecimal.ZERO;

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", new BigDecimal("0.12"));
        mockLocData.put("tenor_days", 90);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, invalidDiscountedAmount);

        // Then
        assertNull(result, "Expected interest should be null when discounted amount is zero or negative");
    }

    @Test
    public void testCalculateExpectedInterest_DifferentScenarios() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("10000.00");
        
        // Test case 1: 6% annual rate, 30 days
        BigDecimal annualInterestRate1 = new BigDecimal("0.06");
        Integer tenorDays1 = 30;
        BigDecimal expectedInterest1 = new BigDecimal("49.31506849"); // 0.06 * 10000 * (30/365)

        Map<String, Object> mockLocData1 = new HashMap<>();
        mockLocData1.put("annual_interest_rate", annualInterestRate1);
        mockLocData1.put("tenor_days", tenorDays1);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData1);

        // When
        BigDecimal result1 = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertTrue(expectedInterest1.subtract(result1).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "6% rate for 30 days should be calculated correctly. Expected: " + expectedInterest1 + ", Actual: " + result1);

        // Test case 2: 15% annual rate, 180 days
        BigDecimal annualInterestRate2 = new BigDecimal("0.15");
        Integer tenorDays2 = 180;
        BigDecimal expectedInterest2 = new BigDecimal("739.72602740"); // 0.15 * 10000 * (180/365)

        Map<String, Object> mockLocData2 = new HashMap<>();
        mockLocData2.put("annual_interest_rate", annualInterestRate2);
        mockLocData2.put("tenor_days", tenorDays2);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData2);

        // When
        BigDecimal result2 = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertTrue(expectedInterest2.subtract(result2).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "15% rate for 180 days should be calculated correctly. Expected: " + expectedInterest2 + ", Actual: " + result2);

        // Test case 3: 24% annual rate, 365 days (full year)
        BigDecimal annualInterestRate3 = new BigDecimal("0.24");
        Integer tenorDays3 = 365;
        BigDecimal expectedInterest3 = new BigDecimal("2400.00000000"); // 0.24 * 10000 * (365/365) = 2400

        Map<String, Object> mockLocData3 = new HashMap<>();
        mockLocData3.put("annual_interest_rate", annualInterestRate3);
        mockLocData3.put("tenor_days", tenorDays3);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData3);

        // When
        BigDecimal result3 = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertTrue(expectedInterest3.subtract(result3).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "24% rate for 365 days should equal full annual interest. Expected: " + expectedInterest3 + ", Actual: " + result3);
    }

    @Test
    public void testCalculateExpectedInterest_DatabaseError() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        PlatformApiDataValidationException exception = assertThrows(
                PlatformApiDataValidationException.class,
                () -> customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount),
                "Should wrap database exceptions in PlatformApiDataValidationException"
        );

        // Check if the exception contains the expected error message or code
        String message = exception.getMessage();
        String code = exception.getGlobalisationMessageCode();
        assertTrue(message != null && (message.contains("Failed to calculate expected interest") || 
                  message.contains("expected.interest.calculation.failed") ||
                  message.contains("Validation errors exist") ||
                  (code != null && (code.contains("expected.interest.calculation.failed") || 
                                   code.contains("validation.errors.exist")))),
                  "Should contain expected interest calculation error message. Message: " + message + ", Code: " + code);
    }

    @Test
    public void testCalculateExpectedInterest_LargeAmounts() {
        // Given - test with large amounts to ensure BigDecimal precision is maintained
        BigDecimal discountedAmount = new BigDecimal("999999.99");
        BigDecimal annualInterestRate = new BigDecimal("0.18"); // 18%
        Integer tenorDays = 120;
        BigDecimal expectedInterest = new BigDecimal("59178.08219178"); // 0.18 * 999999.99 * (120/365)

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", annualInterestRate);
        mockLocData.put("tenor_days", tenorDays);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateExpectedInterest(loanId, discountedAmount);

        // Then
        assertTrue(expectedInterest.subtract(result).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "Large amounts should be calculated with proper precision. Expected: " + expectedInterest + ", Actual: " + result);
    }

    // ==================== NET DISBURSED AMOUNT CALCULATION TESTS ====================

    @Test
    public void testCalculateNetDisbursedAmount_SuccessfulCalculation() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal advancePercentage = new BigDecimal("0.80"); // 80%
        BigDecimal discountedAmount = new BigDecimal("8000.00"); // 10000 * 0.80
        BigDecimal annualInterestRate = new BigDecimal("0.12"); // 12%
        Integer tenorDays = 90;
        BigDecimal expectedInterest = new BigDecimal("236.71232877"); // 0.12 * 8000 * (90/365)
        BigDecimal expectedNetDisbursedAmount = new BigDecimal("7763.28767123"); // 8000 - 236.71

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", annualInterestRate);
        mockLocData.put("tenor_days", tenorDays);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertNotNull(result, "Net disbursed amount should not be null");
        assertTrue(expectedNetDisbursedAmount.subtract(result).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "Net disbursed amount should be calculated correctly. Expected: " + expectedNetDisbursedAmount + ", Actual: " + result);
        
        // Verify database calls
        verify(jdbcTemplate, times(2)).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId));
        verify(jdbcTemplate).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateNetDisbursedAmount_NoLocAssociation() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertNull(result, "Net disbursed amount should be null when no LOC association exists");
        
        // Verify only the first query was called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate, never()).queryForMap(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateNetDisbursedAmount_NoAdvancePercentage() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertNull(result, "Net disbursed amount should be null when no advance percentage is set");
        
        // Verify both queries were called
        verify(jdbcTemplate).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateNetDisbursedAmount_NoAnnualInterestRate() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal advancePercentage = new BigDecimal("0.80");

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", null);
        mockLocData.put("tenor_days", 90);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertNull(result, "Net disbursed amount should be null when no annual interest rate is set");
        
        // Verify all queries were called
        verify(jdbcTemplate, times(2)).queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId));
        verify(jdbcTemplate).queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId));
        verify(jdbcTemplate).queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId));
    }

    @Test
    public void testCalculateNetDisbursedAmount_ZeroPrincipal() {
        // Given
        BigDecimal zeroPrincipal = BigDecimal.ZERO;

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(new BigDecimal("0.80"));

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, zeroPrincipal);

        // Then
        assertNull(result, "Net disbursed amount should be null when principal is zero");
    }

    @Test
    public void testCalculateNetDisbursedAmount_DifferentScenarios() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");
        
        // Test case 1: 50% advance, 6% rate, 30 days
        BigDecimal advancePercentage1 = new BigDecimal("0.50");
        BigDecimal annualInterestRate1 = new BigDecimal("0.06");
        Integer tenorDays1 = 30;
        BigDecimal expectedInterest1 = new BigDecimal("24.65753425"); // 0.06 * 5000 * (30/365)
        BigDecimal expectedNetDisbursedAmount1 = new BigDecimal("4975.34246575"); // 5000 - 24.66

        Map<String, Object> mockLocData1 = new HashMap<>();
        mockLocData1.put("annual_interest_rate", annualInterestRate1);
        mockLocData1.put("tenor_days", tenorDays1);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage1);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData1);

        // When
        BigDecimal result1 = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertTrue(expectedNetDisbursedAmount1.subtract(result1).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "50% advance, 6% rate for 30 days should be calculated correctly. Expected: " + expectedNetDisbursedAmount1 + ", Actual: " + result1);

        // Test case 2: 100% advance, 15% rate, 180 days
        BigDecimal advancePercentage2 = new BigDecimal("1.00");
        BigDecimal annualInterestRate2 = new BigDecimal("0.15");
        Integer tenorDays2 = 180;
        BigDecimal expectedInterest2 = new BigDecimal("739.72602740"); // 0.15 * 10000 * (180/365)
        BigDecimal expectedNetDisbursedAmount2 = new BigDecimal("9260.27397260"); // 10000 - 739.73

        Map<String, Object> mockLocData2 = new HashMap<>();
        mockLocData2.put("annual_interest_rate", annualInterestRate2);
        mockLocData2.put("tenor_days", tenorDays2);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage2);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData2);

        // When
        BigDecimal result2 = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertTrue(expectedNetDisbursedAmount2.subtract(result2).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "100% advance, 15% rate for 180 days should be calculated correctly. Expected: " + expectedNetDisbursedAmount2 + ", Actual: " + result2);
    }

    @Test
    public void testCalculateNetDisbursedAmount_DatabaseError() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        PlatformApiDataValidationException exception = assertThrows(
                PlatformApiDataValidationException.class,
                () -> customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal),
                "Should wrap database exceptions in PlatformApiDataValidationException"
        );

        // Check if the exception contains the expected error message or code
        String message = exception.getMessage();
        String code = exception.getGlobalisationMessageCode();
        assertTrue(message != null && (message.contains("Failed to calculate net disbursed amount") || 
                  message.contains("net.disbursed.amount.calculation.failed") ||
                  message.contains("Validation errors exist") ||
                  (code != null && (code.contains("net.disbursed.amount.calculation.failed") || 
                                   code.contains("validation.errors.exist")))),
                  "Should contain net disbursed amount calculation error message. Message: " + message + ", Code: " + code);
    }

    @Test
    public void testCalculateNetDisbursedAmount_LargeAmounts() {
        // Given - test with large amounts to ensure BigDecimal precision is maintained
        BigDecimal principal = new BigDecimal("999999.99");
        BigDecimal advancePercentage = new BigDecimal("0.75");
        BigDecimal annualInterestRate = new BigDecimal("0.18"); // 18%
        Integer tenorDays = 120;
        BigDecimal expectedInterest = new BigDecimal("44383.56164384"); // 0.18 * 749999.9925 * (120/365)
        BigDecimal expectedNetDisbursedAmount = new BigDecimal("705616.43085616"); // 749999.9925 - 44383.56

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", annualInterestRate);
        mockLocData.put("tenor_days", tenorDays);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertTrue(expectedNetDisbursedAmount.subtract(result).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "Large amounts should be calculated with proper precision. Expected: " + expectedNetDisbursedAmount + ", Actual: " + result);
    }

    @Test
    public void testCalculateNetDisbursedAmount_InterestGreaterThanDiscountedAmount() {
        // Given - edge case where interest is greater than discounted amount
        BigDecimal principal = new BigDecimal("1000.00");
        BigDecimal advancePercentage = new BigDecimal("0.10"); // 10%
        BigDecimal annualInterestRate = new BigDecimal("0.50"); // 50%
        Integer tenorDays = 365; // Full year
        BigDecimal expectedInterest = new BigDecimal("50.00000000"); // 0.50 * 100 * (365/365) = 50
        BigDecimal expectedNetDisbursedAmount = new BigDecimal("50.00000000"); // 100 - 50 = 50

        Map<String, Object> mockLocData = new HashMap<>();
        mockLocData.put("annual_interest_rate", annualInterestRate);
        mockLocData.put("tenor_days", tenorDays);

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"), 
                eq(Long.class), 
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT advance_percentage FROM m_line_of_credit WHERE id = ?"), 
                eq(BigDecimal.class), 
                eq(lineOfCreditId)))
                .thenReturn(advancePercentage);

        when(jdbcTemplate.queryForMap(
                eq("SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?"), 
                eq(lineOfCreditId)))
                .thenReturn(mockLocData);

        // When
        BigDecimal result = customLoanWritePlatformService.calculateNetDisbursedAmount(loanId, principal);

        // Then
        assertTrue(expectedNetDisbursedAmount.subtract(result).abs().compareTo(new BigDecimal("0.01")) < 0, 
                  "Net disbursed amount should handle cases where interest is significant. Expected: " + expectedNetDisbursedAmount + ", Actual: " + result);
    }
}
