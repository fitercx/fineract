package com.crediblex.fineract.portfolio.loc.charge.api;

import com.crediblex.fineract.portfolio.loc.charge.commands.LocChargeCommandWrapperBuilder;
import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeReadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * Jersey resource for Line of Credit Charges (Option 1 design: settled via linked savings, no LOC transaction ledger).
 */
@Path("/v1/lineofcredits/{locId}/charges")
@Component
@RequiredArgsConstructor
public class LineOfCreditChargesApiResource {

    private final PlatformSecurityContext securityContext;
    private final LineOfCreditChargeReadService readService;
    private final FromJsonHelper jsonHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final ObjectMapper objectMapper;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@PathParam("locId") Long locId, @QueryParam("status") String status, @QueryParam("asOfDate") String asOfDateStr) {
        securityContext.authenticatedUser().validateHasReadPermission("LOC_CHARGE");
        String normalized = StringUtils.defaultIfBlank(status, "active").toLowerCase();
        List<LocChargeData> result;
        switch (normalized) {
            case "active":
            case "all":
                result = readService.listActive(locId);
            break;
            case "due":
            case "overdue": // both map to same underlying service for now
                LocalDate asOf = asOfDateStr != null ? LocalDate.parse(asOfDateStr) : LocalDate.now();
                result = readService.listDueOrOverdue(locId, asOf);
            break;
            default:
                return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Unsupported status value\"}").build();
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("{chargeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOne(@PathParam("locId") Long locId, @PathParam("chargeId") Long chargeId) {
        securityContext.authenticatedUser().validateHasReadPermission("LOC_CHARGE");
        LocChargeData data = readService.getOne(locId, chargeId);
        return Response.ok(data).build();
    }

    @POST
    @Path("{chargeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postCommand(@PathParam("locId") Long locId, @PathParam("chargeId") Long chargeId,
            @QueryParam("command") String command) {
        if (StringUtils.equalsIgnoreCase(command, "waive")) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("locId", locId);
            node.put("chargeId", chargeId);
            String json = node.toString();
            CommandWrapper commandRequest = new LocChargeCommandWrapperBuilder().waiveLocCharge(locId, chargeId).withJson(json).build();
            var result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Unsupported command\"}").build();
    }

    @DELETE
    @Path("{chargeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("locId") Long locId, @PathParam("chargeId") Long chargeId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("locId", locId);
        node.put("chargeId", chargeId);
        String json = node.toString();
        CommandWrapper commandRequest = new LocChargeCommandWrapperBuilder().deleteLocCharge(locId, chargeId).withJson(json).build();
        var result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return Response.ok(result).build();
    }

}
