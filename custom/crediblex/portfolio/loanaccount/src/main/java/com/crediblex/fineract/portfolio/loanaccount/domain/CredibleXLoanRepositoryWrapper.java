package com.crediblex.fineract.portfolio.loanaccount.domain;

import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class CredibleXLoanRepositoryWrapper extends LoanRepositoryWrapper {

    public CredibleXLoanRepositoryWrapper(LoanRepository repository, FineractProperties fineractProperties) {
        super(repository, fineractProperties);
    }

    @Transactional(readOnly = true)
    public Loan findOneWithoutNotFoundDetection(final ExternalId externalId) {
        return repository.findByExternalId(externalId).orElse(null);
    }
}
