package com.crediblex.fineract.portfolio.loc.serialization;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.avro.generic.GenericContainer;
import org.apache.fineract.avro.generator.ByteBufferSerializable;
import org.apache.fineract.avro.loc.v1.LineOfCreditDataV1;
import org.apache.fineract.infrastructure.event.business.domain.BusinessEvent;
import org.apache.fineract.infrastructure.event.external.service.serialization.serializer.AbstractBusinessEventWithCustomDataSerializer;
import org.apache.fineract.infrastructure.event.external.service.serialization.serializer.ExternalEventCustomDataSerializer;
import org.springframework.stereotype.Component;
import com.crediblex.fineract.portfolio.loc.event.LineOfCreditBusinessEvent;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

@Component
@RequiredArgsConstructor
public class LineOfCreditBusinessEventSerializer
        extends AbstractBusinessEventWithCustomDataSerializer<LineOfCreditBusinessEvent> {

    private final LineOfCreditV1Mapper mapper;
    private final List<ExternalEventCustomDataSerializer<LineOfCreditBusinessEvent>> externalEventCustomDataSerializers;

    @Override
    public <T> ByteBufferSerializable toAvroDTO(BusinessEvent<T> rawEvent) {
        LineOfCreditBusinessEvent event = (LineOfCreditBusinessEvent) rawEvent;
        LineOfCredit loc = event.get();

        LineOfCreditDataV1 dto = mapper.map(loc);
        dto.setCustomData(collectCustomData(event));
        return dto;
    }

    @Override
    public <T> boolean canSerialize(BusinessEvent<T> event) {
        return event instanceof LineOfCreditBusinessEvent;
    }

    @Override
    public Class<? extends GenericContainer> getSupportedSchema() {
        return LineOfCreditDataV1.class;
    }

    @Override
    protected List<ExternalEventCustomDataSerializer<LineOfCreditBusinessEvent>> getExternalEventCustomDataSerializers() {
        return externalEventCustomDataSerializers;
    }
}