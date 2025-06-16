package com.crediblex.fineract.commands;

import com.crediblex.fineract.commands.data.ExtendedAuditData;
import com.crediblex.fineract.commands.repository.EzySqlLoanChargeWaiverRepository;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.data.AuditData;
import org.apache.fineract.commands.data.AuditSearchData;
import org.apache.fineract.commands.service.AuditReadPlatformService;
import org.apache.fineract.commands.service.AuditReadPlatformServiceImpl;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.crediblex.fineract.commands.queries.AuditQueries.LoanChargeWaiveDetails;
import static java.util.stream.Collectors.toMap;

@Primary
@Service
@RequiredArgsConstructor
public class CredXAuditReadPlatformService implements AuditReadPlatformService {

    private final AuditReadPlatformServiceImpl originalAuditReadPlatformService;
    private final EzySqlLoanChargeWaiverRepository loanChargeWaiverRepository;


    @Override
    public List<AuditData> retrieveAuditEntries(SQLBuilder extraCriteria, boolean includeJson) {
        return originalAuditReadPlatformService.retrieveAuditEntries(extraCriteria, includeJson);
    }

    @Override
    public Page<AuditData> retrievePaginatedAuditEntries(SQLBuilder extraCriteria, boolean includeJson, PaginationParameters parameters) {
        return originalAuditReadPlatformService.retrievePaginatedAuditEntries(extraCriteria, includeJson, parameters);
    }

    @Override
    public AuditData retrieveAuditEntry(Long auditId) {
        return originalAuditReadPlatformService.retrieveAuditEntry(auditId);
    }

    @Override
    public AuditSearchData retrieveSearchTemplate(String useType) {
        return originalAuditReadPlatformService.retrieveSearchTemplate(useType);
    }

    private static boolean isWaiveCharge(AuditData data) {
        return "WAIVE".equals(data.getActionName()) && "LOANCHARGE".equals(data.getEntityName());
    }

    public List<AuditData> retrieveAllEntriesToBeChecked(SQLBuilder extraCriteria, boolean includeJson) {
        //the optimal solution for this would have been to modify the query downstream
        //however, to minimize changes, let us enhance the data after it has been fetched
        List<AuditData> auditData = originalAuditReadPlatformService.retrieveAllEntriesToBeChecked(extraCriteria, includeJson);
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
                .collect(toMap(c -> c.getLoanChargeId(), c -> c));

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

}

