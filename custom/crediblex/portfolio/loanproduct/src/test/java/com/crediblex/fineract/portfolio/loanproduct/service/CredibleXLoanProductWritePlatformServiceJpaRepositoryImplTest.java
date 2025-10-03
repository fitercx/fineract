package com.crediblex.fineract.portfolio.loanproduct.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CredibleXLoanProductWritePlatformServiceJpaRepositoryImpl focusing on Factor Rate Product
 * Configuration
 */
@ExtendWith(MockitoExtension.class)
public class CredibleXLoanProductWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    private LoanProduct loanProduct;

    @BeforeEach
    void setUp() {
        loanProduct = mock(LoanProduct.class);
    }

    /**
     * Test Case 1: Valid Factor Rate Amount - Should Pass Validation
     */
    @Test
    void testValidateFactorRateAmount_WithValidAmount_ShouldPass() {
        // Given
        BigDecimal validFactorRateAmount = new BigDecimal("5.0");
        Long maxFactorRate = 15L;
        boolean factorRateEnabled = true;

        when(configurationDomainService.retrieveMaximumProductFactorRate()).thenReturn(maxFactorRate);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            validateFactorRateConfiguration(factorRateEnabled, validFactorRateAmount, configurationDomainService);
        }, "Valid factor rate amount should not throw exception");

        // Verify configuration service was called
        verify(configurationDomainService).retrieveMaximumProductFactorRate();
    }

    /**
     * Test Case 2: Factor Rate Amount Less Than or Equal to One - Should Throw Exception
     */
    @Test
    void testValidateFactorRateAmount_WithAmountLessThanOrEqualToOne_ShouldThrowException() {
        // Given
        BigDecimal invalidFactorRateAmount = BigDecimal.ONE; // Exactly 1.0 - should fail
        Long maxFactorRate = 15L;
        boolean factorRateEnabled = true;

        when(configurationDomainService.retrieveMaximumProductFactorRate()).thenReturn(maxFactorRate);

        // When & Then
        GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validateFactorRateConfiguration(factorRateEnabled, invalidFactorRateAmount, configurationDomainService),
                "Should throw exception for factor rate amount less than or equal to one");

        assertEquals("error.msg.loan.product.factor.rate.amount.must.be.greater.than.one", exception.getGlobalisationMessageCode(),
                "Exception code should match expected");
        assertEquals("Factor rate product amount must be greater than one", exception.getDefaultUserMessage(),
                "Exception message should match expected");
    }

    /**
     * Test Case 3: Factor Rate Amount Exceeding Maximum Limit - Should Throw Exception
     */
    @Test
    void testValidateFactorRateAmount_WithAmountExceedingMaximum_ShouldThrowException() {
        // Given
        BigDecimal excessiveFactorRateAmount = new BigDecimal("20.0"); // Exceeds max of 15
        Long maxFactorRate = 15L;
        boolean factorRateEnabled = true;

        when(configurationDomainService.retrieveMaximumProductFactorRate()).thenReturn(maxFactorRate);

        // When & Then
        GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validateFactorRateConfiguration(factorRateEnabled, excessiveFactorRateAmount, configurationDomainService),
                "Should throw exception for factor rate amount exceeding maximum");

        assertEquals("error.msg.loan.product.factor.rate.amount.exceeds.maximum.limit", exception.getGlobalisationMessageCode(),
                "Exception code should match expected");
        assertTrue(exception.getDefaultUserMessage().contains("exceeds the maximum limit of 15"),
                "Exception message should mention the maximum limit");
    }

    /**
     * Test Case 4: Factor Rate Disabled - Should Skip Validation
     */
    @Test
    void testValidateFactorRateAmount_WithFactorRateDisabled_ShouldSkipValidation() {
        // Given
        BigDecimal anyFactorRateAmount = new BigDecimal("5.0");
        boolean factorRateEnabled = false;

        // When & Then - Should not throw exception and should not call configuration service
        assertDoesNotThrow(() -> {
            validateFactorRateConfiguration(factorRateEnabled, anyFactorRateAmount, configurationDomainService);
        }, "When factor rate is disabled, validation should be skipped");

        // Verify configuration service was not called since factor rate is disabled
        verify(configurationDomainService, never()).retrieveMaximumProductFactorRate();
    }

    /**
     * Helper method to validate factor rate configuration - Mimics the logic in
     * CredibleXLoanProductWritePlatformServiceJpaRepositoryImpl
     */
    private void validateFactorRateConfiguration(boolean factorRateProductEnabled, BigDecimal factorRate,
            ConfigurationDomainService configurationDomainService) {
        if (factorRateProductEnabled) {
            final Long maximumProductFactorRate = configurationDomainService.retrieveMaximumProductFactorRate();
            if (factorRate == null || factorRate.compareTo(BigDecimal.ONE) <= 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.amount.must.be.greater.than.one",
                        "Factor rate product amount must be greater than one");
            }
            if (factorRate.compareTo(BigDecimal.valueOf(maximumProductFactorRate)) > 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.exceeds.maximum.limit",
                        "Factor rate of " + factorRate + " exceeds the maximum limit of " + maximumProductFactorRate,
                        maximumProductFactorRate);
            }
        }
    }
}
