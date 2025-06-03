package com.crediblex.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;

import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.account.data.StandingInstructionDuesData;
import org.apache.fineract.portfolio.account.service.PortfolioAccountReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
public class CustomStandingInstructionReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ClientReadPlatformService clientReadPlatformService;

    @Mock
    private OfficeReadPlatformService officeReadPlatformService;

    @Mock
    private PortfolioAccountReadPlatformService portfolioAccountReadPlatformService;

    @Mock
    private DropdownReadPlatformService dropdownReadPlatformService;

    @Mock
    private ColumnValidator columnValidator;

    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    @Mock
    private PaginationHelper paginationHelper;

    @Mock
    private ResultSet resultSet;

    private CustomStandingInstructionReadPlatformServiceImpl customService;

    @BeforeEach
    public void setUp() {
        customService = new CustomStandingInstructionReadPlatformServiceImpl(
                jdbcTemplate,
                clientReadPlatformService,
                officeReadPlatformService,
                portfolioAccountReadPlatformService,
                dropdownReadPlatformService,
                columnValidator,
                sqlGenerator,
                paginationHelper);
    }

    @Test
    public void testRetriveLoanDuesData_shouldUseCorrectSqlSyntaxForPostgreSQL() {
        // Given
        Long loanId = 123L;
        LocalDate businessDate = LocalDate.of(2023, 6, 15);
        String expectedBusinessDateSql = "DATE '2023-06-15'";

        StandingInstructionDuesData expectedDuesData = new StandingInstructionDuesData(
                businessDate,
                BigDecimal.valueOf(1000.00));

        // When
        when(sqlGenerator.currentBusinessDate()).thenReturn(expectedBusinessDateSql);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(expectedDuesData);

        // Execute
        StandingInstructionDuesData result = customService.retriveLoanDuesData(loanId);

        // Then
        assertNotNull(result);
        assertEquals(expectedDuesData, result);

        // Verify SQL contains correct boolean syntax for PostgreSQL
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(RowMapper.class), eq(new Object[] { loanId }));

        String capturedSql = sqlCaptor.getValue();
        assertNotNull(capturedSql);
        // Verify SQL uses "completed_derived = false" instead of "completed_derived <> 1"
        assert(capturedSql.contains("completed_derived = false"));
    }
}