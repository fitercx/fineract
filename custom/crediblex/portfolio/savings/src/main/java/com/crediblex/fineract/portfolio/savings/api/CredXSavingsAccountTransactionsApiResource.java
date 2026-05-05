package com.crediblex.fineract.portfolio.savings.api;

import com.crediblex.fineract.portfolio.savings.data.CredXSavingsTransactionSubTypeData;
import com.crediblex.fineract.portfolio.savings.service.CredXSavingsTransactionSubTypeService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.springframework.stereotype.Component;

@Path("/v1/crediblex/savingsaccounts/{savingsId}/transactions")
@Component
@RequiredArgsConstructor
public class CredXSavingsAccountTransactionsApiResource {

    private final PlatformSecurityContext context;
    private final CredXSavingsTransactionSubTypeService transactionSubTypeService;
    private final DefaultToApiJsonSerializer<Map<Long, CredXSavingsTransactionSubTypeData>> toApiJsonSerializer;

    @GET
    @Path("subtypes")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveTransactionSubTypes(@PathParam("savingsId") final Long savingsId) {
        this.context.authenticatedUser().validateHasReadPermission(SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME);
        return this.toApiJsonSerializer.serialize(this.transactionSubTypeService.retrieveSubTypes(savingsId));
    }
}
