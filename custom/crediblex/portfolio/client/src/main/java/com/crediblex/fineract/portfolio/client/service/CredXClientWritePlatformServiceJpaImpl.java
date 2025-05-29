package com.crediblex.fineract.portfolio.client.service;

import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.address.service.AddressWritePlatformService;
import org.apache.fineract.portfolio.client.data.ClientDataValidator;
import org.apache.fineract.portfolio.client.domain.ClientNonPersonRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.service.ClientFamilyMembersWritePlatformService;
import org.apache.fineract.portfolio.client.service.ClientWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CredXClientWritePlatformServiceJpaImpl extends ClientWritePlatformServiceJpaRepositoryImpl {

    public CredXClientWritePlatformServiceJpaImpl(PlatformSecurityContext context, ClientRepositoryWrapper clientRepository, ClientNonPersonRepositoryWrapper clientNonPersonRepository, OfficeRepositoryWrapper officeRepositoryWrapper, NoteRepository noteRepository, GroupRepository groupRepository, ClientDataValidator fromApiJsonDeserializer, AccountNumberGenerator accountNumberGenerator, StaffRepositoryWrapper staffRepository, CodeValueRepositoryWrapper codeValueRepository, org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper loanRepositoryWrapper,
                                               org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper savingsRepositoryWrapper,
                                               org.apache.fineract.portfolio.savings.domain.SavingsProductRepository savingsProductRepository, org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService savingsApplicationProcessWritePlatformService, CommandProcessingService commandProcessingService, ConfigurationDomainService configurationDomainService, AccountNumberFormatRepositoryWrapper accountNumberFormatRepository, FromJsonHelper fromApiJsonHelper, AddressWritePlatformService addressWritePlatformService, ClientFamilyMembersWritePlatformService clientFamilyMembersWritePlatformService, BusinessEventNotifierService businessEventNotifierService, EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, ExternalIdFactory externalIdFactory) {
        super(context, clientRepository, clientNonPersonRepository, officeRepositoryWrapper, noteRepository, groupRepository, fromApiJsonDeserializer, accountNumberGenerator, staffRepository, codeValueRepository, loanRepositoryWrapper, savingsRepositoryWrapper, savingsProductRepository, savingsApplicationProcessWritePlatformService, commandProcessingService, configurationDomainService, accountNumberFormatRepository, fromApiJsonHelper, addressWritePlatformService, clientFamilyMembersWritePlatformService, businessEventNotifierService, entityDatatableChecksWritePlatformService, externalIdFactory);
    }

}
