package com.crediblex.fineract.commands;

import static com.crediblex.fineract.commands.queries.AuditQueries.LoanChargeWaiveDetails;
import static java.util.stream.Collectors.toMap;

import com.crediblex.fineract.commands.data.ExtendedAuditData;
import com.crediblex.fineract.commands.repository.EzySqlLoanChargeWaiverRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.data.AuditData;
import org.apache.fineract.commands.data.AuditSearchData;
import org.apache.fineract.commands.service.AuditReadPlatformService;
import org.apache.fineract.commands.service.AuditReadPlatformServiceImpl;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
public class CredXAuditReadPlatformService implements AuditReadPlatformService {

    private final AuditReadPlatformServiceImpl originalAuditReadPlatformService;
    private final EzySqlLoanChargeWaiverRepository loanChargeWaiverRepository;
    private final SavingsAccountReadPlatformService savingsAccountReadPlatformService;
    private final SavingsAccountRepository savingsAccountRepository;

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

    private static boolean isSavingsUndo(AuditData data) {
        return "UNDOTRANSACTION".equalsIgnoreCase(data.getActionName()) && "SAVINGSACCOUNT".equalsIgnoreCase(data.getEntityName());
    }

    private static boolean isSavingsDeposit(AuditData data) {
        return "DEPOSIT".equalsIgnoreCase(data.getActionName()) && "SAVINGSACCOUNT".equalsIgnoreCase(data.getEntityName());
    }

    public List<AuditData> retrieveAllEntriesToBeChecked(SQLBuilder extraCriteria, boolean includeJson) {
        // the optimal solution for this would have been to modify the query downstream
        // however, to minimize changes, let us enhance the data after it has been fetched
        List<AuditData> auditData = originalAuditReadPlatformService.retrieveAllEntriesToBeChecked(extraCriteria, includeJson);
        List<AuditData> enhancedAuditData = new ArrayList<>();

        // Collect all charge IDs that need enhancement
        List<Long> chargeIds = auditData.stream().filter(CredXAuditReadPlatformService::isWaiveCharge).map(AuditData::getResourceId)
                .toList();

        // Fetch all charge waiver details in a single query (only if needed)
        List<LoanChargeWaiveDetails.Result> waiveDetails = chargeIds.isEmpty() ? List.of()
                : loanChargeWaiverRepository.fetchLoanChargeWaiverDetails(chargeIds);

        // Create a lookup map for quick access
        @SuppressWarnings("Convert2MethodRef")
        Map<Long, LoanChargeWaiveDetails.Result> detailsMap = waiveDetails.stream().collect(toMap(c -> c.getLoanChargeId(), c -> c));

        // Enhance the audit data
        for (AuditData data : auditData) {
            AuditData enhancedData;

            // Case 1: LOANCHARGE WAIVE - use specialized query
            if (isWaiveCharge(data)) {
                Long chargeId = data.getResourceId();
                LoanChargeWaiveDetails.Result details = detailsMap.get(chargeId);
                if (details != null) {
                    enhancedData = ExtendedAuditData.from(data, details.getClientName(), details.getLoanId(), details.getWaiveOffAmount());
                } else {
                    enhancedData = data;
                }
            }
            // Case 2: SAVINGSACCOUNT UNDOTRANSACTION - enrich with clientName via savings account
            else if (isSavingsUndo(data)) {
                enhancedData = enrichSavingsTransactionWithClient(data);
            }
            // Case 3: SAVINGSACCOUNT DEPOSIT - enrich with clientName via savings account
            else if (isSavingsDeposit(data)) {
                enhancedData = enrichSavingsTransactionWithClient(data);
            }
            // Default: no change
            else {
                enhancedData = data;
            }

            enhancedAuditData.add(enhancedData);
        }

        return enhancedAuditData;
    }

    private AuditData enrichSavingsTransactionWithClient(AuditData data) {
        try {
            if (hasClientName(data)) {
                return data;
            }

            // For DEPOSIT action, resourceId is null and savings account number is available
            // Try to get client name via savings account number first
            String clientNameFromAccountNo = getClientNameFromSavingsAccountNo(data.getSavingsAccountNo());
            if (clientNameFromAccountNo != null) {
                return ExtendedAuditData.from(data, clientNameFromAccountNo, null, null);
            }

            // Fallback: try to get savings account id from resourceId or subresourceId
            String clientNameFromId = getClientNameFromSavingsId(data);
            if (clientNameFromId != null) {
                return ExtendedAuditData.from(data, clientNameFromId, null, null);
            }

            return data;
        } catch (Exception e) {
            // Be defensive: never break the audit listing
            return data;
        }
    }

    private boolean hasClientName(AuditData data) {
        return data.getClientName() != null && !data.getClientName().isBlank();
    }

    private String getClientNameFromSavingsAccountNo(String savingsAccountNo) {
        if (savingsAccountNo == null || savingsAccountNo.isBlank()) {
            return null;
        }
        SavingsAccount savingsAccount = savingsAccountRepository.findSavingsAccountByAccountNumber(savingsAccountNo);
        if (savingsAccount == null || savingsAccount.getClient() == null) {
            return null;
        }
        String clientName = savingsAccount.getClient().getDisplayName();
        return (clientName != null && !clientName.isBlank()) ? clientName : null;
    }

    private String getClientNameFromSavingsId(AuditData data) {
        Long savingsId = data.getResourceId();
        if (savingsId == null || savingsId <= 0) {
            savingsId = data.getSubresourceId();
        }
        if (savingsId == null || savingsId <= 0) {
            return null;
        }
        SavingsAccountData savings = savingsAccountReadPlatformService.retrieveOne(savingsId);
        if (savings == null || savings.getClientName() == null || savings.getClientName().isBlank()) {
            return null;
        }
        return savings.getClientName();
    }

}
