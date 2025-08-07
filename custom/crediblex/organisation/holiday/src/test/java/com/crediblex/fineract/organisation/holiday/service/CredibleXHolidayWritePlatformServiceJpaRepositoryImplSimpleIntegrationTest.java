package com.crediblex.fineract.organisation.holiday.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.data.HolidayDataValidator;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.holiday.domain.HolidayStatusType;
import org.apache.fineract.organisation.holiday.domain.RescheduleType;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Simple integration test for CredibleXHolidayWritePlatformServiceJpaRepositoryImpl
 * Focuses on core functionality without complex threading scenarios
 */
@ExtendWith(MockitoExtension.class)
class CredibleXHolidayWritePlatformServiceJpaRepositoryImplSimpleIntegrationTest {

    @Mock
    private HolidayDataValidator fromApiJsonDeserializer;
    @Mock
    private HolidayRepositoryWrapper holidayRepository;
    @Mock
    private WorkingDaysRepositoryWrapper daysRepositoryWrapper;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private LoanScheduleService loanScheduleService;
    @Mock
    private LoanUtilService loanUtilService;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private LoanTermVariationsMapper loanTermVariationsMapper;
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private TransactionTemplate transactionTemplate;

    private CredibleXHolidayWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(
                fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, 
                officeRepositoryWrapper, fromApiJsonHelper, loanRepositoryWrapper, 
                loanScheduleService, loanUtilService, configurationDomainService, loanTermVariationsMapper,
                taskExecutor, transactionTemplate);
        
        // Setup ThreadLocalContextUtil with business dates
        HashMap<org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType.BUSINESS_DATE, LocalDate.now());
        businessDates.put(org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType.COB_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        
        // Setup task executor to return proper Future objects
        lenient().when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        
        // Setup transaction template - just do nothing for void methods
        lenient().doNothing().when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void testUpdateHolidayWithActiveStateAndDateChanges() {
        // Setup test data
        Long holidayId = 1L;
        LocalDate originalFromDate = LocalDate.of(2024, 4, 4);
        LocalDate originalToDate = LocalDate.of(2024, 4, 4);
        LocalDate newFromDate = LocalDate.of(2024, 4, 5);
        LocalDate newToDate = LocalDate.of(2024, 4, 5);
        
        // Create active holiday
        Holiday holiday = createHoliday(holidayId, "Test Holiday", originalFromDate, originalToDate, null, 1);
        holiday.setStatus(HolidayStatusType.ACTIVE.getValue());
        
        // Create office
        Office office = createOffice(1L, "Test Office");
        Set<Office> offices = Set.of(office);
        holiday.setOffices(offices);
        
        // Create loan with installments
        Loan loan = createLoanWithInstallments();
        
        // Mock repository calls
        lenient().when(context.authenticatedUser()).thenReturn(null);
        lenient().when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        lenient().when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(any(), any()))
                .thenReturn(Arrays.asList(loan));
        lenient().when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(any(), any()))
                .thenReturn(new ArrayList<>());
        lenient().when(loanRepositoryWrapper.findOneWithNotFoundDetection(anyLong())).thenReturn(loan);
        
        // Create JSON command for update
        JsonCommand command = createJsonCommand(holidayId, newFromDate, newToDate, null, 1);
        
        // Execute update
        CommandProcessingResult result = service.updateHoliday(command);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        assertTrue(result.hasChanges());
        
        // Verify that the holiday was updated
        verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(newFromDate, holiday.getFromDate());
        assertEquals(newToDate, holiday.getToDate());
    }

    @Test
    void testDeleteHolidayWithType1Reschedule() {
        // Setup test data
        Long holidayId = 2L;
        LocalDate holidayFromDate = LocalDate.of(2024, 4, 4);
        LocalDate holidayToDate = LocalDate.of(2024, 4, 4);
        
        // Create holiday (Type 1 - reschedule to next repayment date)
        Holiday holiday = createHoliday(holidayId, "Test Holiday", holidayFromDate, holidayToDate, null, 1);
        
        // Create office
        Office office = createOffice(1L, "Test Office");
        Set<Office> offices = Set.of(office);
        holiday.setOffices(offices);
        
        // Create loan with installments
        Loan loan = createLoanWithInstallments();
        
        // Mock repository calls
        lenient().when(context.authenticatedUser()).thenReturn(null);
        lenient().when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        lenient().when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(any(), any()))
                .thenReturn(Arrays.asList(loan));
        lenient().when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(any(), any()))
                .thenReturn(new ArrayList<>());
        
        // Execute delete
        CommandProcessingResult result = service.deleteHoliday(holidayId);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        
        // Verify that the holiday was marked as deleted
        verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(HolidayStatusType.DELETED.getValue(), holiday.getStatus());
    }

    @Test
    void testUpdateHolidayWithDataIntegrityException() {
        // Setup test data
        Long holidayId = 3L;
        LocalDate holidayFromDate = LocalDate.of(2024, 4, 4);
        LocalDate holidayToDate = LocalDate.of(2024, 4, 4);
        
        // Create holiday
        Holiday holiday = createHoliday(holidayId, "Test Holiday", holidayFromDate, holidayToDate, null, 1);
        
        // Create a proper exception with a cause
        RuntimeException cause = new RuntimeException("Database constraint violation");
        DataIntegrityViolationException dataIntegrityException = new DataIntegrityViolationException("Duplicate holiday name", cause);
        
        // Mock repository calls to throw exception
        lenient().when(context.authenticatedUser()).thenReturn(null);
        lenient().when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        lenient().doThrow(dataIntegrityException)
                .when(holidayRepository).saveAndFlush(any(Holiday.class));
        
        // Create JSON command
        JsonCommand command = createJsonCommand(holidayId, holidayFromDate, holidayToDate, null, 1);
        
        // Execute update and expect exception
        assertThrows(org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException.class, () -> {
            service.updateHoliday(command);
        });
    }

    @Test
    void testUpdateHolidayWithNameAndDescriptionChanges() {
        // Setup test data
        Long holidayId = 4L;
        LocalDate holidayFromDate = LocalDate.of(2024, 4, 4);
        LocalDate holidayToDate = LocalDate.of(2024, 4, 4);
        String newName = "Updated Holiday Name";
        String newDescription = "Updated holiday description";
        
        // Create holiday
        Holiday holiday = createHoliday(holidayId, "Original Name", holidayFromDate, holidayToDate, null, 1);
        holiday.setStatus(HolidayStatusType.ACTIVE.getValue());
        
        // Create office
        Office office = createOffice(1L, "Test Office");
        Set<Office> offices = Set.of(office);
        holiday.setOffices(offices);
        
        // Mock repository calls
        lenient().when(context.authenticatedUser()).thenReturn(null);
        lenient().when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        
        // Create JSON command with name and description changes
        JsonCommand command = createJsonCommandWithNameAndDescription(holidayId, holidayFromDate, holidayToDate, null, 1, newName, newDescription);
        
        // Execute update
        CommandProcessingResult result = service.updateHoliday(command);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        assertTrue(result.hasChanges());
        
        // Verify that the holiday was updated
        verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(newName, holiday.getName());
        assertEquals(newDescription, holiday.getDescription());
    }

    // Helper methods
    private Holiday createHoliday(Long id, String name, LocalDate fromDate, LocalDate toDate, LocalDate rescheduleTo, int rescheduleType) {
        Holiday holiday = new Holiday();
        holiday.setId(id);
        holiday.setName(name);
        holiday.setFromDate(fromDate);
        holiday.setToDate(toDate);
        holiday.setRepaymentsRescheduledTo(rescheduleTo);
        holiday.setReschedulingType(RescheduleType.fromInt(rescheduleType).getValue());
        holiday.setStatus(HolidayStatusType.ACTIVE.getValue());
        return holiday;
    }

    private Office createOffice(Long id, String name) {
        Office office = Office.headOffice(name, LocalDate.of(2024, 1, 1), null);
        // Use reflection to set the ID for testing
        try {
            java.lang.reflect.Field idField = Office.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(office, id);
        } catch (Exception e) {
            throw new RuntimeException("Error setting office ID", e);
        }
        return office;
    }

    private Loan createLoanWithInstallments() {
        // Create a minimal loan using reflection
        try {
            // Use reflection to create a loan instance
            java.lang.reflect.Constructor<Loan> constructor = Loan.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Loan loan = constructor.newInstance();
            
            // Set basic properties using reflection - id is in AbstractPersistableCustom
            java.lang.reflect.Field idField = AbstractPersistableCustom.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(loan, 1L);
            
            // Set loan status
            java.lang.reflect.Field statusField = Loan.class.getDeclaredField("loanStatus");
            statusField.setAccessible(true);
            statusField.set(loan, LoanStatus.ACTIVE);
            
            // Create installments
            List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
            
            // Disbursement installment
            LoanRepaymentScheduleInstallment disbursement = new LoanRepaymentScheduleInstallment();
            disbursement.setInstallmentNumber(0);
            disbursement.setDueDate(LocalDate.of(2024, 1, 4));
            installments.add(disbursement);
            
            // Regular installments
            for (int i = 1; i <= 6; i++) {
                LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment();
                installment.setInstallmentNumber(i);
                installment.setDueDate(LocalDate.of(2024, i, 4));
                // For the first installment, fromDate should be the disbursement date (Jan 4)
                LocalDate fromDate = (i == 1) ? LocalDate.of(2024, 1, 4) : LocalDate.of(2024, i - 1, 4);
                installment.setFromDate(fromDate);
                installments.add(installment);
            }
            
            // Set installments using reflection
            java.lang.reflect.Field installmentsField = Loan.class.getDeclaredField("repaymentScheduleInstallments");
            installmentsField.setAccessible(true);
            installmentsField.set(loan, installments);
            
            return loan;
        } catch (Exception e) {
            throw new RuntimeException("Error creating loan", e);
        }
    }

    private JsonCommand createJsonCommand(Long entityId, LocalDate fromDate, LocalDate toDate, LocalDate rescheduleTo, int rescheduleType) {
        JsonCommand command = Mockito.mock(JsonCommand.class);
        lenient().when(command.entityId()).thenReturn(entityId);
        lenient().when(command.commandId()).thenReturn(1L);
        lenient().when(command.dateFormat()).thenReturn("dd MMMM yyyy");
        lenient().when(command.locale()).thenReturn("en");
        
        // Mock parameter checks - only mock what's actually used
        lenient().when(command.isChangeInLocalDateParameterNamed(eq("fromDate"), any())).thenReturn(true);
        lenient().when(command.localDateValueOfParameterNamed("fromDate")).thenReturn(fromDate);
        lenient().when(command.stringValueOfParameterNamed("fromDate")).thenReturn(fromDate.toString());
        
        lenient().when(command.isChangeInLocalDateParameterNamed(eq("toDate"), any())).thenReturn(true);
        lenient().when(command.localDateValueOfParameterNamed("toDate")).thenReturn(toDate);
        lenient().when(command.stringValueOfParameterNamed("toDate")).thenReturn(toDate.toString());
        
        // Only mock what's actually used by the service
        lenient().when(command.isChangeInLocalDateParameterNamed(eq("repaymentsRescheduledTo"), any())).thenReturn(false);
        lenient().when(command.isChangeInStringParameterNamed(eq("name"), any())).thenReturn(false);
        lenient().when(command.isChangeInStringParameterNamed(eq("description"), any())).thenReturn(false);
        lenient().when(command.isChangeInIntegerParameterNamed(eq("reschedulingType"), any())).thenReturn(false);
        lenient().when(command.hasParameter("offices")).thenReturn(false);
        
        return command;
    }

    private JsonCommand createJsonCommandWithNameAndDescription(Long entityId, LocalDate fromDate, LocalDate toDate, LocalDate rescheduleTo, int rescheduleType, String name, String description) {
        JsonCommand command = createJsonCommand(entityId, fromDate, toDate, rescheduleTo, rescheduleType);
        
        // Mock name and description changes
        lenient().when(command.isChangeInStringParameterNamed(eq("name"), any())).thenReturn(true);
        lenient().when(command.stringValueOfParameterNamed("name")).thenReturn(name);
        
        lenient().when(command.isChangeInStringParameterNamed(eq("description"), any())).thenReturn(true);
        lenient().when(command.stringValueOfParameterNamed("description")).thenReturn(description);
        
        return command;
    }
} 