package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.client.domain.Client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "m_line_of_credit")
@Getter
@Setter
public class LineOfCredit extends AbstractAuditableWithUTCDateTimeCustom<Long> {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "product_type", length = 50, nullable = false)
    private String productType;

    @Column(name = "maximum_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal maximumAmount;

    @Column(name = "available_balance", precision = 19, scale = 6, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "consumed_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal consumedAmount;

    @Column(name = "activation_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivationStatus activationStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Default constructor.
     */
    protected LineOfCredit() {
    }

    /**
     * Constructor for creating a new Line of Credit.
     */
    public LineOfCredit(Client client, String name, String productType, BigDecimal maximumAmount,
                        LocalDate startDate, LocalDate endDate) {
        this.client = client;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.availableBalance = maximumAmount;
        this.consumedAmount = BigDecimal.ZERO;
        this.activationStatus = ActivationStatus.INACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Activate the line of credit.
     */
    public void activate() {
        this.activationStatus = ActivationStatus.ACTIVE;
    }

    /**
     * Deactivate the line of credit.
     */
    public void deactivate() {
        this.activationStatus = ActivationStatus.INACTIVE;
    }

    /**
     * Update the line of credit with changes from command.
     */
    public Map<String, Object> update(JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

        if (command.isChangeInStringParameterNamed("name", this.name)) {
            final String newValue = command.stringValueOfParameterNamed("name");
            actualChanges.put("name", newValue);
            this.name = newValue;
        }

        if (command.isChangeInStringParameterNamed("productType", this.productType)) {
            final String newValue = command.stringValueOfParameterNamed("productType");
            actualChanges.put("productType", newValue);
            this.productType = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("maximumAmount", this.maximumAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("maximumAmount");
            actualChanges.put("maximumAmount", newValue);
            this.maximumAmount = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("startDate", this.startDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("startDate");
            actualChanges.put("startDate", newValue);
            this.startDate = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("endDate", this.endDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("endDate");
            actualChanges.put("endDate", newValue);
            this.endDate = newValue;
        }

        return actualChanges;
    }

    /**
     * Activation status enum for Line of Credit.
     */
    public enum ActivationStatus {
        ACTIVE, INACTIVE, SUSPENDED, CLOSED
    }
}
