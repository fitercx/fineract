package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestStatusEnumData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestEnumerations;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
public class CustomLoanRescheduleRequestReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService codeValueReadPlatformService;

    @InjectMocks
    private CustomLoanRescheduleRequestReadPlatformServiceImpl customLoanRescheduleRequestReadPlatformService;

    @BeforeEach
    public void setup() {
        // Initialize business dates
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        
        // Set up tenant context
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);
    }

    @Test
    public void testRetrieveAllRescheduleRequests() {
        // Given
        String command = "all";
        Long loanId = 1L;
        
        List<LoanRescheduleRequestData> mockRescheduleRequests = new ArrayList<>();
        
        // Create a mock reschedule request data
        Integer statusEnum = 100; // Pending approval
        LoanRescheduleRequestStatusEnumData statusEnumData = LoanRescheduleRequestEnumerations.status(statusEnum);
        String clientName = "Test Client";
        String loanAccountNumber = "LOAN-123456";
        Long clientId = 100L;
        LocalDate rescheduleFromDate = LocalDate.of(2025, 7, 15);
        Long rescheduleReasonCvId = 1L;
        CodeValueData rescheduleReasonCodeValue = CodeValueData.instance(rescheduleReasonCvId, "Economic difficulties");
        String rescheduleReasonComment = "Client requested due to financial hardship";
        Boolean recalculateInterest = true;
        Integer rescheduleFromInstallment = 5;
        
        LoanRescheduleRequestData requestData = LoanRescheduleRequestData.instance(
                1L, // id
                loanId,
                statusEnumData,
                rescheduleFromInstallment,
                rescheduleFromDate,
                rescheduleReasonCodeValue,
                rescheduleReasonComment,
                null, // timeline
                clientName,
                loanAccountNumber,
                clientId,
                recalculateInterest, // This should be true, not null
                Collections.emptyList(), // reasons
                Collections.emptyList()  // variations data
        );
        
        mockRescheduleRequests.add(requestData);
        
        // Mock the JDBC query execution
        when(jdbcTemplate.query(Mockito.anyString(), Mockito.any(RowMapper.class), Mockito.eq(loanId)))
            .thenReturn(mockRescheduleRequests);
        
        // When
        List<LoanRescheduleRequestData> result = customLoanRescheduleRequestReadPlatformService.retrieveAllRescheduleRequests(command, loanId);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        LoanRescheduleRequestData resultData = result.get(0);
        assertEquals(1L, resultData.getId());
        assertEquals(loanId, resultData.getLoanId());
        assertEquals(statusEnum.longValue(), resultData.getStatusEnum().id());
        assertEquals(clientName, resultData.getClientName());
        assertEquals(loanAccountNumber, resultData.getLoanAccountNumber());
        assertEquals(clientId, resultData.getClientId());
        assertEquals(rescheduleFromDate, resultData.getRescheduleFromDate());
        assertEquals(rescheduleReasonCvId, resultData.getRescheduleReasonCodeValueId().getId());
    }
    
    @Test
    public void testRetrieveAllRescheduleReasons() {
        // Given
        String loanRescheduleReason = "LoanRescheduleReason";
        
        // Mock the CodeValueReadPlatformService
        List<CodeValueData> mockCodeValues = new ArrayList<>();
        mockCodeValues.add(CodeValueData.instance(1L, "Economic difficulties"));
        mockCodeValues.add(CodeValueData.instance(2L, "Change in payment cycle"));
        
        when(codeValueReadPlatformService.retrieveCodeValuesByCode(loanRescheduleReason))
            .thenReturn(mockCodeValues);
            
        // When
        LoanRescheduleRequestData result = customLoanRescheduleRequestReadPlatformService.retrieveAllRescheduleReasons(loanRescheduleReason);
        
        // Then
        assertNotNull(result);
    }
    
    @Test
    public void testReadLoanRescheduleRequest() {
        // Given
        Long requestId = 1L;
        
        // Create a mock reschedule request data
        Long loanId = 1L;
        Integer statusEnum = 100; // Pending approval
        LoanRescheduleRequestStatusEnumData statusEnumData = LoanRescheduleRequestEnumerations.status(statusEnum);
        String clientName = "Test Client";
        String loanAccountNumber = "LOAN-123456";
        Long clientId = 100L;
        LocalDate rescheduleFromDate = LocalDate.of(2025, 7, 15);
        Long rescheduleReasonCvId = 1L;
        CodeValueData rescheduleReasonCodeValue = CodeValueData.instance(rescheduleReasonCvId, "Economic difficulties");
        String rescheduleReasonComment = "Client requested due to financial hardship";
        Boolean recalculateInterest = true;
        Integer rescheduleFromInstallment = 5;
        
        LoanRescheduleRequestData mockRequestData = LoanRescheduleRequestData.instance(
                requestId,
                loanId,
                statusEnumData,
                rescheduleFromInstallment,
                rescheduleFromDate,
                rescheduleReasonCodeValue,
                rescheduleReasonComment,
                null, // timeline
                clientName,
                loanAccountNumber,
                clientId,
                recalculateInterest,
                Collections.emptyList(), // reasons
                Collections.emptyList()  // variations data
        );
        
        // Mock the JDBC query execution
        when(jdbcTemplate.queryForObject(Mockito.anyString(), Mockito.any(RowMapper.class), Mockito.eq(requestId)))
            .thenReturn(mockRequestData);
            
        // When
        LoanRescheduleRequestData result = customLoanRescheduleRequestReadPlatformService.readLoanRescheduleRequest(requestId);
        
        // Then
        assertNotNull(result);
        assertEquals(requestId, result.getId());
        assertEquals(loanId, result.getLoanId());
        assertEquals(statusEnum.longValue(), result.getStatusEnum().id());
        assertEquals(clientName, result.getClientName());
        assertEquals(loanAccountNumber, result.getLoanAccountNumber());
        assertEquals(clientId, result.getClientId());
        assertEquals(rescheduleFromDate, result.getRescheduleFromDate());
        assertEquals(rescheduleReasonCvId, result.getRescheduleReasonCodeValueId().getId());
        assertEquals(rescheduleReasonComment, result.getRescheduleReasonComment());
        assertEquals(recalculateInterest, result.getRecalculateInterest());
        assertEquals(rescheduleFromInstallment, result.getRescheduleFromInstallment());
    }
}
