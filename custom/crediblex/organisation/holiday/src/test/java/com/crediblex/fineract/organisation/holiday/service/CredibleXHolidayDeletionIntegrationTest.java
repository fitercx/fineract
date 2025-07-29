package com.crediblex.fineract.organisation.holiday.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
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

import com.google.gson.JsonObject;

/**
 * Integration test for CredibleXHolidayWritePlatformServiceJpaRepositoryImpl
 * Tests the holiday deletion functionality with realistic data
 */
@ExtendWith(MockitoExtension.class)
class CredibleXHolidayDeletionIntegrationTest {

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

    private CredibleXHolidayWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(
                fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, 
                officeRepositoryWrapper, fromApiJsonHelper, loanRepositoryWrapper, 
                loanScheduleService, loanUtilService, configurationDomainService, loanTermVariationsMapper);
    }

    @Test
    void testDeleteHolidayWithType1Reschedule() {
        // Setup test data
        Long holidayId = 1L;
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
        Mockito.when(context.authenticatedUser()).thenReturn(null);
        Mockito.when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        Mockito.when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(Arrays.asList(loan));
        Mockito.when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        
        // Execute delete
        CommandProcessingResult result = service.deleteHoliday(holidayId);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        
        // Verify that the holiday was marked as deleted
        Mockito.verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(HolidayStatusType.DELETED.getValue(), holiday.getStatus());
        
        // Verify that loan was saved (indicating installments were restored)
        Mockito.verify(loanRepositoryWrapper).saveAndFlush(loan);
    }

    @Test
    void testDeleteHolidayWithSpecificRescheduleDate() {
        // Setup test data
        Long holidayId = 2L;
        LocalDate holidayFromDate = LocalDate.of(2024, 4, 4);
        LocalDate holidayToDate = LocalDate.of(2024, 4, 4);
        LocalDate rescheduleToDate = LocalDate.of(2024, 4, 8);
        
        // Create holiday (Type 2 - specific reschedule date)
        Holiday holiday = createHoliday(holidayId, "Test Holiday", holidayFromDate, holidayToDate, rescheduleToDate, 2);
        
        // Create office
        Office office = createOffice(1L, "Test Office");
        Set<Office> offices = Set.of(office);
        holiday.setOffices(offices);
        
        // Create loan with installments
        Loan loan = createLoanWithInstallments();
        
        // Mock repository calls
        Mockito.when(context.authenticatedUser()).thenReturn(null);
        Mockito.when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        Mockito.when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(Arrays.asList(loan));
        Mockito.when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        
        // Execute delete
        CommandProcessingResult result = service.deleteHoliday(holidayId);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        
        // Verify that the holiday was marked as deleted
        Mockito.verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(HolidayStatusType.DELETED.getValue(), holiday.getStatus());
    }

    @Test
    void testNoLoansAffected() {
        // Setup test data
        Long holidayId = 3L;
        LocalDate holidayFromDate = LocalDate.of(2024, 4, 4);
        LocalDate holidayToDate = LocalDate.of(2024, 4, 4);
        
        // Create holiday
        Holiday holiday = createHoliday(holidayId, "Test Holiday", holidayFromDate, holidayToDate, null, 1);
        
        // Create office
        Office office = createOffice(1L, "Test Office");
        Set<Office> offices = Set.of(office);
        holiday.setOffices(offices);
        
        // Mock repository calls - no loans found
        Mockito.when(context.authenticatedUser()).thenReturn(null);
        Mockito.when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        Mockito.when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        Mockito.when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        
        // Execute delete
        CommandProcessingResult result = service.deleteHoliday(holidayId);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        
        // Verify that the holiday was marked as deleted
        Mockito.verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(HolidayStatusType.DELETED.getValue(), holiday.getStatus());
        
        // Verify that no loans were saved
        Mockito.verify(loanRepositoryWrapper, Mockito.never()).saveAndFlush(Mockito.any(Loan.class));
    }

    @Test
    void testUpdateHolidayWithActiveState() {
        // Setup test data
        Long holidayId = 4L;
        LocalDate originalFromDate = LocalDate.of(2024, 4, 4);
        LocalDate originalToDate = LocalDate.of(2024, 4, 4);
        LocalDate newFromDate = LocalDate.of(2024, 4, 5);
        LocalDate newToDate = LocalDate.of(2024, 4, 5); // Update toDate as well to keep it valid
        
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
        Mockito.when(context.authenticatedUser()).thenReturn(null);
        Mockito.when(holidayRepository.findOneWithNotFoundDetection(holidayId)).thenReturn(holiday);
        Mockito.when(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(Arrays.asList(loan));
        Mockito.when(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        
        // Create JSON command for update
        JsonCommand command = createJsonCommand(holidayId, newFromDate, newToDate, null, 1);
        
        // Execute update
        CommandProcessingResult result = service.updateHoliday(command);
        
        // Verify results
        assertNotNull(result);
        assertEquals(holidayId, result.getResourceId());
        
        // Verify that the holiday was updated
        Mockito.verify(holidayRepository).saveAndFlush(holiday);
        assertEquals(newFromDate, holiday.getFromDate());
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
        Mockito.when(command.entityId()).thenReturn(entityId);
        Mockito.when(command.commandId()).thenReturn(1L);
        Mockito.when(command.dateFormat()).thenReturn("dd MMMM yyyy");
        Mockito.when(command.locale()).thenReturn("en");
        
        // Mock parameter checks
        Mockito.when(command.isChangeInLocalDateParameterNamed(Mockito.eq("fromDate"), Mockito.any())).thenReturn(true);
        Mockito.when(command.localDateValueOfParameterNamed("fromDate")).thenReturn(fromDate);
        Mockito.when(command.stringValueOfParameterNamed("fromDate")).thenReturn(fromDate.toString());
        
        Mockito.when(command.isChangeInLocalDateParameterNamed(Mockito.eq("toDate"), Mockito.any())).thenReturn(true);
        Mockito.when(command.localDateValueOfParameterNamed("toDate")).thenReturn(toDate);
        Mockito.when(command.stringValueOfParameterNamed("toDate")).thenReturn(toDate.toString());
        Mockito.when(command.isChangeInLocalDateParameterNamed(Mockito.eq("repaymentsRescheduledTo"), Mockito.any())).thenReturn(false);
        Mockito.when(command.isChangeInStringParameterNamed(Mockito.eq("name"), Mockito.any())).thenReturn(false);
        Mockito.when(command.isChangeInStringParameterNamed(Mockito.eq("description"), Mockito.any())).thenReturn(false);
        Mockito.when(command.isChangeInIntegerParameterNamed(Mockito.eq("reschedulingType"), Mockito.any())).thenReturn(false);
        Mockito.when(command.hasParameter("offices")).thenReturn(false);
        
        return command;
    }
} 