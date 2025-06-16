package com.crediblex.fineract.commands;

import com.crediblex.fineract.commands.data.ExtendedAuditData;
import com.crediblex.fineract.commands.queries.AuditQueries;
import com.crediblex.fineract.commands.repository.EzySqlLoanChargeWaiverRepository;
import org.apache.fineract.commands.data.AuditData;
import org.apache.fineract.commands.service.AuditReadPlatformServiceImpl;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CredXAuditReadPlatformServiceTest {

    @Mock
    private EzySqlLoanChargeWaiverRepository loanChargeWaiverRepository;
    @Mock
    private AuditReadPlatformServiceImpl auditReadPlatformService;

    @InjectMocks
    private CredXAuditReadPlatformService credXAuditReadPlatformService;

    @BeforeEach
    void setUp() {
        // We need to spy the service to mock the super.retrieveAllEntriesToBeChecked call
    }

    @Test
    void retrieveAllEntriesToBeChecked_whenNoWaiveCharges_shouldReturnOriginalList() {
        // Arrange
        SQLBuilder sqlBuilder = mock(SQLBuilder.class);
        List<AuditData> originalList = new ArrayList<>();
        originalList.add(createAuditData(1L, "CREATE", "CLIENT", 100L));
        originalList.add(createAuditData(2L, "UPDATE", "LOAN", 200L));
        
        when(auditReadPlatformService.retrieveAllEntriesToBeChecked(any(SQLBuilder.class), any(Boolean.class))).thenReturn(originalList);
        
        // Act
        List<AuditData> result = credXAuditReadPlatformService.retrieveAllEntriesToBeChecked(sqlBuilder, true);
        
        // Assert
        assertSame(originalList, result);
        verify(loanChargeWaiverRepository, times(0)).fetchLoanChargeWaiverDetails(anyList());
    }

    @Test
    void retrieveAllEntriesToBeChecked_withWaiveCharges_shouldEnhanceData() {
        // Arrange
        SQLBuilder sqlBuilder = mock(SQLBuilder.class);
        List<AuditData> originalList = new ArrayList<>();
        originalList.add(createAuditData(1L, "WAIVE", "LOANCHARGE", 100L));
        originalList.add(createAuditData(2L, "WAIVE", "LOANCHARGE", 200L));
        originalList.add(createAuditData(3L, "CREATE", "CLIENT", 300L));
        
        List<AuditQueries.LoanChargeWaiveDetails.Result> waiveDetails = new ArrayList<>();
        waiveDetails.add(createWaiveDetail(100L, "Client 1", 1000L, new BigDecimal("50.00")));
        waiveDetails.add(createWaiveDetail(200L, "Client 2", 2000L, new BigDecimal("75.50")));
        
        when(auditReadPlatformService.retrieveAllEntriesToBeChecked(any(SQLBuilder.class), any(Boolean.class)))
            .thenReturn(originalList);
        when(loanChargeWaiverRepository.fetchLoanChargeWaiverDetails(anyList()))
            .thenReturn(waiveDetails);
        
        // Act
        List<AuditData> result = credXAuditReadPlatformService.retrieveAllEntriesToBeChecked(sqlBuilder, true);
        
        // Assert
        assertEquals(3, result.size());
        
        // First two items should be ExtendedAuditData
        assertInstanceOf(ExtendedAuditData.class, result.get(0));
        assertInstanceOf(ExtendedAuditData.class, result.get(1));
        
        // Third item should be regular AuditData
        assertFalse(result.get(2) instanceof ExtendedAuditData);
        
        // Verify enhanced data
        ExtendedAuditData enhanced1 = (ExtendedAuditData) result.get(0);
        assertEquals("Client 1", enhanced1.getClientName());
        assertEquals(1000L, enhanced1.getLoanId());
        assertEquals(new BigDecimal("50.00"), enhanced1.getWaiveOffAmount());
        
        ExtendedAuditData enhanced2 = (ExtendedAuditData) result.get(1);
        assertEquals("Client 2", enhanced2.getClientName());
        assertEquals(2000L, enhanced2.getLoanId());
        assertEquals(new BigDecimal("75.50"), enhanced2.getWaiveOffAmount());
        
        // Verify repository was called with correct charge IDs
        List<Long> expectedChargeIds = List.of(100L, 200L);
        verify(loanChargeWaiverRepository).fetchLoanChargeWaiverDetails(expectedChargeIds);
    }

    @Test
    void retrieveAllEntriesToBeChecked_withWaiveChargesButNoMatchingDetails_shouldReturnOriginalData() {
        // Arrange
        SQLBuilder sqlBuilder = mock(SQLBuilder.class);
        List<AuditData> originalList = new ArrayList<>();
        originalList.add(createAuditData(1L, "WAIVE", "LOANCHARGE", 100L));
        originalList.add(createAuditData(2L, "WAIVE", "LOANCHARGE", 200L));
        
        // Return empty list from repository
        when(loanChargeWaiverRepository.fetchLoanChargeWaiverDetails(anyList()))
            .thenReturn(Collections.emptyList());
        
        when(auditReadPlatformService.retrieveAllEntriesToBeChecked(any(SQLBuilder.class), any(Boolean.class)))
            .thenReturn(originalList);
        
        // Act
        List<AuditData> result = credXAuditReadPlatformService.retrieveAllEntriesToBeChecked(sqlBuilder, true);
        
        // Assert
        assertEquals(2, result.size());
        
        // Both items should be the original AuditData objects
        assertSame(originalList.get(0), result.get(0));
        assertSame(originalList.get(1), result.get(1));
        
        // Verify repository was called
        verify(loanChargeWaiverRepository).fetchLoanChargeWaiverDetails(List.of(100L, 200L));
    }

    @Test
    void retrieveAllEntriesToBeChecked_withPartialMatchingDetails_shouldEnhanceOnlyMatchingData() {
        // Arrange
        SQLBuilder sqlBuilder = mock(SQLBuilder.class);
        List<AuditData> originalList = new ArrayList<>();
        originalList.add(createAuditData(1L, "WAIVE", "LOANCHARGE", 100L));
        originalList.add(createAuditData(2L, "WAIVE", "LOANCHARGE", 200L));
        
        // Only return details for one charge
        List<AuditQueries.LoanChargeWaiveDetails.Result> waiveDetails = new ArrayList<>();
        waiveDetails.add(createWaiveDetail(100L, "Client 1", 1000L, new BigDecimal("50.00")));
        
        when(auditReadPlatformService.retrieveAllEntriesToBeChecked(any(SQLBuilder.class), any(Boolean.class)))
            .thenReturn(originalList);
        when(loanChargeWaiverRepository.fetchLoanChargeWaiverDetails(List.of(100L,200L)))
            .thenReturn(waiveDetails);
        
        // Act
        List<AuditData> result = credXAuditReadPlatformService.retrieveAllEntriesToBeChecked(sqlBuilder, true);
        
        // Assert
        assertEquals(2, result.size());
        
        // First item should be ExtendedAuditData
        assertInstanceOf(ExtendedAuditData.class, result.get(0));
        
        // Second item should be the original AuditData
        assertSame(originalList.get(1), result.get(1));
        
        // Verify enhanced data
        ExtendedAuditData enhanced = (ExtendedAuditData) result.get(0);
        assertEquals("Client 1", enhanced.getClientName());
        assertEquals(1000L, enhanced.getLoanId());
        assertEquals(new BigDecimal("50.00"), enhanced.getWaiveOffAmount());
    }

    // Helper methods to create test data
    private AuditData createAuditData(Long id, String actionName, String entityName, Long resourceId) {
        return new AuditData(
            id, actionName, entityName, resourceId, null, "maker", ZonedDateTime.now(),
            null, null, "success", "{}", "Office 1", null, null,
            "Client Name", "LN-001", null, 1L, 1L, "/api/url"
        );
    }
    
    private AuditQueries.LoanChargeWaiveDetails.Result createWaiveDetail(
            Long chargeId, String clientName, Long loanId, BigDecimal waiveAmount) {
        AuditQueries.LoanChargeWaiveDetails.Result result = new AuditQueries.LoanChargeWaiveDetails.Result();
        result.setField("loanChargeId", chargeId);
        result.setField("clientName", clientName);
        result.setField("loanId", loanId);
        result.setField("waiveOffAmount", waiveAmount);
        return result;
    }
}