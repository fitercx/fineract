package com.crediblex.fineract.portfolio.loanaccount.jobs.generateloanmonthlyaccrualsummations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.data.LoanMonthlyAccrualData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAudit;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAuditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class GenerateLoanMonthlyAccrualSummationsTaskletTest {

    @Mock
    private LoanMonthlyAccrualJobAuditRepository loanMonthlyAccrualJobAuditRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    @InjectMocks
    private GenerateLoanMonthlyAccrualSummationsTasklet tasklet;

    @Test
    @DisplayName("should create separate monthly accrual audit records per loan and month")
    void shouldCreateSeparateAccrualsPerMonthForSameLoan() throws Exception {
        // given
        Long loanId = 232L;
        LocalDate novemberMonthDate = LocalDate.of(2025, 11, 1);
        LocalDate decemberMonthDate = LocalDate.of(2025, 12, 1);

        LoanMonthlyAccrualData novemberAccrual = LoanMonthlyAccrualData.builder().loanId(loanId).accrualMonthDate(novemberMonthDate)
                .totalInterestAccrualDerived(new BigDecimal("100.00")).accrualTransactionIds("1,2,3").build();

        LoanMonthlyAccrualData decemberAccrual = LoanMonthlyAccrualData.builder().loanId(loanId).accrualMonthDate(decemberMonthDate)
                .totalInterestAccrualDerived(new BigDecimal("200.00")).accrualTransactionIds("4,5").build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(novemberAccrual, decemberAccrual));

        // when
        tasklet.execute(stepContribution, chunkContext);

        // then
        ArgumentCaptor<LoanMonthlyAccrualJobAudit> auditCaptor = ArgumentCaptor.forClass(LoanMonthlyAccrualJobAudit.class);
        verify(loanMonthlyAccrualJobAuditRepository, times(2)).saveAndFlush(auditCaptor.capture());

        List<LoanMonthlyAccrualJobAudit> audits = auditCaptor.getAllValues();
        assertThat(audits).hasSize(2);

        LoanMonthlyAccrualJobAudit first = audits.get(0);
        LoanMonthlyAccrualJobAudit second = audits.get(1);

        // verify common fields
        assertThat(first.getLoanId()).isEqualTo(loanId);
        assertThat(second.getLoanId()).isEqualTo(loanId);

        // verify November accrual
        assertThat(first.getAccrualMonth()).isEqualTo(novemberMonthDate);
        assertThat(first.getGeneratedOnDate()).isEqualTo(LocalDate.of(2025, 11, 30));
        assertThat(first.getTotalInterestAccrualDerived()).isEqualByComparingTo("100.00");

        // verify December accrual
        assertThat(second.getAccrualMonth()).isEqualTo(decemberMonthDate);
        assertThat(second.getGeneratedOnDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(second.getTotalInterestAccrualDerived()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("should do nothing when there are no unprocessed accruals")
    void shouldDoNothingWhenNoAccruals() throws Exception {
        // given
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        // when
        tasklet.execute(stepContribution, chunkContext);

        // then
        verify(loanMonthlyAccrualJobAuditRepository, never()).saveAndFlush(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
