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
        mockLocData.put("available_balance", new BigDecimal("10000.00"));
        mockLocData.put("consumed_amount", new BigDecimal("5000.00"));
        mockLocData.put("maximum_amount", new BigDecimal("15000.00"));
        mockLocData.put("advance_percentage", new BigDecimal("0.80"));
        mockLocData.put("annual_interest_rate", new BigDecimal("0.12"));
        mockLocData.put("tenor_days", 90);
        mockLocData.put("product_type", "RECEIVABLE");
    }

    // ==================== LOC BALANCE COMPUTATION TESTS ====================






    // ==================== DISCOUNTED AMOUNT CALCULATION TESTS ====================

    @Test
    public void testCalculateDiscountedAmount_SuccessfulCalculation() {
        // Given
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal expectedDiscountedAmount = new BigDecimal("8000.00"); // 10000 * 0.80

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
        BigDecimal result = customLoanWritePlatformService.calculateDiscountedAmount(loanId, principal);

        // Then
        assertNotNull(result, "Discounted amount should not be null");
        assertEquals(0, expectedDiscountedAmount.compareTo(result), "Discounted amount should be calculated correctly");
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
        assertNull(result, "Should return null when no LOC association exists");
    }


    // ==================== EXPECTED INTEREST CALCULATION TESTS ====================


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
        assertNull(result, "Should return null when no LOC association exists");
    }


    // ==================== NET DISBURSED AMOUNT CALCULATION TESTS ====================


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
        assertNull(result, "Should return null when no LOC association exists");
    }


    // ==================== LOC PRODUCT TYPE TESTS ====================

    @Test
    public void testGetLocProductType_SuccessfulRetrieval() {
        // Given
        String expectedProductType = "RECEIVABLE";

        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT product_type FROM m_line_of_credit WHERE id = ?"),
                eq(String.class),
                eq(lineOfCreditId)))
                .thenReturn(expectedProductType);

        // When
        String result = customLoanWritePlatformService.getLocProductType(loanId);

        // Then
        assertEquals(expectedProductType, result, "Product type should match");
    }

    @Test
    public void testGetLocProductType_NoLocAssociation() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(null);

        // When
        String result = customLoanWritePlatformService.getLocProductType(loanId);

        // Then
        assertNull(result, "Should return null when no LOC association exists");
    }


    // ==================== LOC CALCULATION DATA LOGGING TESTS ====================

    @Test
    public void testLogLocCalculationData_SuccessfulLogging() {
        // Given
        BigDecimal discountedAmount = new BigDecimal("8000.00");
        BigDecimal expectedInterest = new BigDecimal("236.71");

        // Mock LOC data retrieval
        Map<String, Object> locData = new HashMap<>();
        locData.put("advance_percentage", new BigDecimal("0.80"));
        locData.put("annual_interest_rate", new BigDecimal("0.12"));
        locData.put("tenor_days", 90);

        when(jdbcTemplate.queryForMap(
                eq("SELECT advance_percentage, annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = " +
                   "(SELECT line_of_credit_id FROM m_loan WHERE id = ?)"),
                eq(loanId)))
                .thenReturn(locData);

        // When
        customLoanWritePlatformService.logLocCalculationData(loanId, discountedAmount, expectedInterest);

        // Then
        verify(jdbcTemplate).queryForMap(
                eq("SELECT advance_percentage, annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = " +
                   "(SELECT line_of_credit_id FROM m_loan WHERE id = ?)"),
                eq(loanId));
    }

    // ==================== EXPECTED INTEREST FOR RECEIVABLE LOAN TESTS ====================


    @Test
    public void testGetExpectedInterestForReceivableLoan_PayableType() {
        // Given - Mock product type as PAYABLE
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT product_type FROM m_line_of_credit WHERE id = ?"),
                eq(String.class),
                eq(lineOfCreditId)))
                .thenReturn("PAYABLE");

        // When
        BigDecimal result = customLoanWritePlatformService.getExpectedInterestForReceivableLoan(loanId);

        // Then
        assertNull(result, "Should return null for PAYABLE type loans");
    }

    @Test
    public void testGetExpectedInterestForReceivableLoan_NoDisbursedAmount() {
        // Given
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        when(jdbcTemplate.queryForObject(
                eq("SELECT product_type FROM m_line_of_credit WHERE id = ?"),
                eq(String.class),
                eq(lineOfCreditId)))
                .thenReturn("RECEIVABLE");

        when(jdbcTemplate.queryForObject(
                eq("SELECT disbursed_amount FROM m_loan WHERE id = ?"),
                eq(BigDecimal.class),
                eq(loanId)))
                .thenReturn(null);

        // When
        BigDecimal result = customLoanWritePlatformService.getExpectedInterestForReceivableLoan(loanId);

        // Then
        assertNull(result, "Should return null when no disbursed amount found");
    }


    // ==================== REPAYMENT LOC BALANCE ADJUSTMENT TESTS ====================

    @Test
    public void testAdjustLocBalanceOnRepayment_SuccessfulAdjustment() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("1000.00");
        BigDecimal currentConsumedAmount = new BigDecimal("5000.00");
        BigDecimal currentAvailableBalance = new BigDecimal("3000.00");

        // Mock LOC association
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        // Mock current LOC balances
        Map<String, Object> locData = new HashMap<>();
        locData.put("consumed_amount", currentConsumedAmount);
        locData.put("available_balance", currentAvailableBalance);

        when(jdbcTemplate.queryForMap(
                eq("SELECT consumed_amount, available_balance FROM m_line_of_credit WHERE id = ?"),
                eq(lineOfCreditId)))
                .thenReturn(locData);

        // Mock LOC balance update
        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        // Mock transaction record creation
        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                   "transaction_date, description, created_date) " +
                   "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())"),
                any(Object[].class)))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertTrue(result, "Should return true for successful adjustment");
        
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId));
        
        verify(jdbcTemplate).update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                   "transaction_date, description, created_date) " +
                   "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())"),
                any(Object[].class));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_NoLocAssociation() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("1000.00");

        // Mock no LOC association
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(null);

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertTrue(result, "Should return true when no LOC association (not an error)");
        
        // Verify no LOC balance updates were attempted
        verify(jdbcTemplate, never()).update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Long.class));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_InvalidRepaymentAmount() {
        // Given
        BigDecimal invalidRepaymentAmount = new BigDecimal("-100.00");

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, invalidRepaymentAmount);

        // Then
        assertFalse(result, "Should return false for invalid repayment amount");
        
        // Verify no database calls were made since the method returns early for invalid amounts
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(Class.class), anyLong());
        verify(jdbcTemplate, never()).queryForMap(anyString(), anyLong());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_RepaymentExceedsConsumedAmount() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("6000.00"); // Exceeds consumed amount
        BigDecimal currentConsumedAmount = new BigDecimal("5000.00");
        BigDecimal currentAvailableBalance = new BigDecimal("3000.00");

        // Mock LOC association
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        // Mock current LOC balances
        Map<String, Object> locData = new HashMap<>();
        locData.put("consumed_amount", currentConsumedAmount);
        locData.put("available_balance", currentAvailableBalance);

        when(jdbcTemplate.queryForMap(
                eq("SELECT consumed_amount, available_balance FROM m_line_of_credit WHERE id = ?"),
                eq(lineOfCreditId)))
                .thenReturn(locData);

        // Mock LOC balance update (consumed amount should be set to 0)
        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        // Mock transaction record creation
        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                   "transaction_date, description, created_date) " +
                   "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())"),
                any(Object[].class)))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertTrue(result, "Should return true for successful adjustment even when repayment exceeds consumed amount");
        
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_NullBalances() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("1000.00");

        // Mock LOC association
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        // Mock null LOC balances
        Map<String, Object> locData = new HashMap<>();
        locData.put("consumed_amount", null);
        locData.put("available_balance", null);

        when(jdbcTemplate.queryForMap(
                eq("SELECT consumed_amount, available_balance FROM m_line_of_credit WHERE id = ?"),
                eq(lineOfCreditId)))
                .thenReturn(locData);

        // Mock LOC balance update
        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(1);

        // Mock transaction record creation
        when(jdbcTemplate.update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                   "transaction_date, description, created_date) " +
                   "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())"),
                any(Object[].class)))
                .thenReturn(1);

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertTrue(result, "Should return true for successful adjustment with null balances");
        
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_UpdateFailure() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("1000.00");
        BigDecimal currentConsumedAmount = new BigDecimal("5000.00");
        BigDecimal currentAvailableBalance = new BigDecimal("3000.00");

        // Mock LOC association
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenReturn(lineOfCreditId);

        // Mock current LOC balances
        Map<String, Object> locData = new HashMap<>();
        locData.put("consumed_amount", currentConsumedAmount);
        locData.put("available_balance", currentAvailableBalance);

        when(jdbcTemplate.queryForMap(
                eq("SELECT consumed_amount, available_balance FROM m_line_of_credit WHERE id = ?"),
                eq(lineOfCreditId)))
                .thenReturn(locData);

        // Mock LOC balance update failure
        when(jdbcTemplate.update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId)))
                .thenReturn(0); // No rows affected

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertFalse(result, "Should return false when update fails");
        
        verify(jdbcTemplate).update(
                eq("UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?"),
                any(BigDecimal.class),
                any(BigDecimal.class),
                eq(lineOfCreditId));
        
        // Verify transaction record creation was not attempted
        verify(jdbcTemplate, never()).update(
                eq("INSERT INTO m_line_of_credit_transactions " +
                   "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                   "transaction_date, description, created_date) " +
                   "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())"),
                any(Object[].class));
    }

    @Test
    public void testAdjustLocBalanceOnRepayment_DatabaseError() {
        // Given
        BigDecimal repaymentAmount = new BigDecimal("1000.00");

        // Mock database error
        when(jdbcTemplate.queryForObject(
                eq("SELECT line_of_credit_id FROM m_loan WHERE id = ?"),
                eq(Long.class),
                eq(loanId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When
        boolean result = customLoanWritePlatformService.adjustLocBalanceOnRepayment(loanId, repaymentAmount);

        // Then
        assertFalse(result, "Should return false when database error occurs");
    }
}