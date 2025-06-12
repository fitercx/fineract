package com.crediblex.fineract.commands;

import com.crediblex.fineract.commands.data.ExtendedAuditData;
import com.crediblex.fineract.commands.repository.EzySqlLoanChargeWaiverRepository;
import org.apache.fineract.commands.data.AuditData;
import org.apache.fineract.commands.service.AuditReadPlatformServiceImpl;
import org.apache.fineract.infrastructure.core.data.PaginationParametersDataValidator;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.crediblex.fineract.commands.queries.AuditQueries.LoanChargeWaiveDetails;
import static java.util.stream.Collectors.toMap;

@Primary
@Service
public class CredXAuditReadPlatformService extends AuditReadPlatformServiceImpl {

    private final EzySqlLoanChargeWaiverRepository loanChargeWaiverRepository;

    public CredXAuditReadPlatformService(JdbcTemplate jdbcTemplate, PlatformSecurityContext context,
                                         FromJsonHelper fromApiJsonHelper, AppUserReadPlatformService appUserReadPlatformService,
                                         OfficeReadPlatformService officeReadPlatformService, ClientReadPlatformService clientReadPlatformService,
                                         LoanProductReadPlatformService loanProductReadPlatformService, StaffReadPlatformService staffReadPlatformService,
                                         PaginationHelper paginationHelper, DatabaseSpecificSQLGenerator sqlGenerator,
                                         PaginationParametersDataValidator paginationParametersDataValidator,
                                         org.apache.fineract.portfolio.savings.service.SavingsProductReadPlatformService savingsProductReadPlatformService,
                                         org.apache.fineract.portfolio.savings.service.DepositProductReadPlatformService depositProductReadPlatformService,
                                         ColumnValidator columnValidator, SqlValidator sqlValidator,
                                         EzySqlLoanChargeWaiverRepository loanChargeWaiverRepository) {
        super(jdbcTemplate, context, fromApiJsonHelper, appUserReadPlatformService, officeReadPlatformService,
                clientReadPlatformService, loanProductReadPlatformService, staffReadPlatformService,
                paginationHelper, sqlGenerator, paginationParametersDataValidator,
                savingsProductReadPlatformService, depositProductReadPlatformService, columnValidator, sqlValidator);
        this.loanChargeWaiverRepository = loanChargeWaiverRepository;
    }



    public List<AuditData> retrieveAllEntriesToBeChecked(SQLBuilder extraCriteria, boolean includeJson) {
        //the optimal solution for this would have been to modify the query downstream
        //however, to minimize changes, let us enhance the data after it has been fetched
        List<AuditData> auditData = super.retrieveAllEntriesToBeChecked(extraCriteria, includeJson);
        List<AuditData> enhancedAuditData = new ArrayList<>();

        // Collect all charge IDs that need enhancement
        List<Long> chargeIds = auditData.stream()
                .filter(CredXAuditReadPlatformService::isWaiveCharge)
                .map(AuditData::getResourceId)
                .toList();

        if (chargeIds.isEmpty()) {
            return auditData; // Return original data if no charge waivers found
        }

        // Fetch all charge waiver details in a single query
        List<LoanChargeWaiveDetails.Result> waiveDetails = loanChargeWaiverRepository
                .fetchLoanChargeWaiverDetails(chargeIds);

        // Create a lookup map for quick access
        @SuppressWarnings("Convert2MethodRef")
        Map<Long, LoanChargeWaiveDetails.Result> detailsMap = waiveDetails.stream()
                .collect(toMap(c -> c.getLaonChargeId(), c -> c));

        // Enhance the audit data
        for (AuditData data : auditData) {
            if (!isWaiveCharge(data)) {
                enhancedAuditData.add(data);
                continue;
            }

            Long chargeId = data.getResourceId();
            LoanChargeWaiveDetails.Result details = detailsMap.get(chargeId);

            if (details == null) {
                enhancedAuditData.add(data);
                continue;
            }

            ExtendedAuditData enhancedData = ExtendedAuditData.from(
                    data,
                    details.getClientName(),
                    details.getLoanId(),
                    details.getWaiveOffAmount()
            );
            enhancedAuditData.add(enhancedData);
        }

        return enhancedAuditData;
    }

    private static boolean isWaiveCharge(AuditData data) {
        return "WAIVE".equals(data.getActionName()) && "LOANCHARGE".equals(data.getEntityName());
    }

}

