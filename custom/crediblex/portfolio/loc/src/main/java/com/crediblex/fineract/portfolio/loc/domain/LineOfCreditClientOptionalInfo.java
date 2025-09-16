package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;

import java.util.Map;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LineOfCreditClientOptionalInfo {


    @Column(name = "client_company_name")
    private String clientCompanyName;

    @Column(name = "client_contact_person_name")
    private String clientContactPersonName;

    @Column(name = "client_contact_person_phone", length = 50)
    private String clientContactPersonPhone;

    @Column(name = "client_contact_person_email")
    private String clientContactPersonEmail;

    @Column(name = "authorized_signatory_name")
    private String authorizedSignatoryName;

    @Column(name = "authorized_signatory_phone", length = 50)
    private String authorizedSignatoryPhone;

    @Column(name = "authorized_signatory_email")
    private String authorizedSignatoryEmail;


    public Map<String,Object> update(Map<String,Object> actualChanges, JsonCommand command){
        if (command.isChangeInStringParameterNamed("clientCompanyName", this.clientCompanyName)) {
            final String newValue = command.stringValueOfParameterNamed("clientCompanyName");
            actualChanges.put("clientCompanyName", newValue);
            this.clientCompanyName = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonName", this.clientContactPersonName)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonName");
            actualChanges.put("clientContactPersonName", newValue);
            this.clientContactPersonName = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonPhone", this.clientContactPersonPhone)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonPhone");
            actualChanges.put("clientContactPersonPhone", newValue);
            this.clientContactPersonPhone = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonEmail", this.clientContactPersonEmail)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonEmail");
            actualChanges.put("clientContactPersonEmail", newValue);
            this.clientContactPersonEmail = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryName", this.authorizedSignatoryName)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryName");
            actualChanges.put("authorizedSignatoryName", newValue);
            this.authorizedSignatoryName = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryPhone", this.authorizedSignatoryPhone)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryPhone");
            actualChanges.put("authorizedSignatoryPhone", newValue);
            this.authorizedSignatoryPhone = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryEmail", this.authorizedSignatoryEmail)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryEmail");
            actualChanges.put("authorizedSignatoryEmail", newValue);
            this.authorizedSignatoryEmail = newValue;
        }

        return actualChanges;
    }
}
