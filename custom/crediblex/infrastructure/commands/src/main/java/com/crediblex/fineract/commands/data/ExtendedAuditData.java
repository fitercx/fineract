package com.crediblex.fineract.commands.data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.Getter;
import org.apache.fineract.commands.data.AuditData;

@Getter
public class ExtendedAuditData extends AuditData {
    private final String clientName;
    private final Long loanId;
    private final BigDecimal waiveOffAmount;

    public ExtendedAuditData(Long id, String actionName, String entityName, Long resourceId, Long subresourceId,
                            String maker, ZonedDateTime madeOnDate, String checker, ZonedDateTime checkedOnDate,
                            String processingResult, String commandAsJson, String officeName, String groupLevelName,
                            String groupName, String clientName, String loanAccountNo, String savingsAccountNo,
                            Long clientId, Long loanId, String url, String extendedClientName, 
                            Long extendedLoanId, BigDecimal waiveOffAmount) {
        super(id, actionName, entityName, resourceId, subresourceId, maker, madeOnDate, checker, checkedOnDate,
                processingResult, commandAsJson, officeName, groupLevelName, groupName, clientName, loanAccountNo,
                savingsAccountNo, clientId, loanId, url);
        this.clientName = extendedClientName;
        this.loanId = extendedLoanId;
        this.waiveOffAmount = waiveOffAmount;
    }

    public static ExtendedAuditData from(AuditData auditData, String clientName, Long loanId, BigDecimal waiveOffAmount) {
        return new ExtendedAuditData(
                auditData.getId(), auditData.getActionName(), auditData.getEntityName(),
                auditData.getResourceId(), auditData.getSubresourceId(), auditData.getMaker(),
                auditData.getMadeOnDate(), auditData.getChecker(), auditData.getCheckedOnDate(),
                auditData.getProcessingResult(), auditData.getCommandAsJson(), auditData.getOfficeName(),
                auditData.getGroupLevelName(), auditData.getGroupName(), auditData.getClientName(),
                auditData.getLoanAccountNo(), auditData.getSavingsAccountNo(), auditData.getClientId(),
                auditData.getLoanId(), auditData.getUrl(), clientName, loanId, waiveOffAmount
        );
    }
}