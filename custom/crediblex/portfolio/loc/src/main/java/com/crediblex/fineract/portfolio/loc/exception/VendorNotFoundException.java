package com.crediblex.fineract.portfolio.loc.exception;

import java.util.Collection;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;

public class VendorNotFoundException extends AbstractPlatformResourceNotFoundException {

    public VendorNotFoundException(final Collection<Long> missingIds) {
        super("error.msg.vendor.id.invalid", "Vendor(s) with identifier(s) "
                + missingIds.stream().map(String::valueOf).collect(Collectors.joining(", ")) + " do not exist", missingIds.toArray());
    }
}
