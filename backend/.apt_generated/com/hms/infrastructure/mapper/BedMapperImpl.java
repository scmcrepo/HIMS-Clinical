package com.hms.infrastructure.mapper;

import com.hms.api.bed.response.BedOccupancyResponse;
import com.hms.domain.bed.model.BedOccupancy;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T10:08:25+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class BedMapperImpl implements BedMapper {

    @Override
    public BedOccupancyResponse toOccupancyResponse(BedOccupancy occupancy) {
        if ( occupancy == null ) {
            return null;
        }

        UUID id = null;
        UUID bedId = null;
        UUID encounterId = null;
        UUID billId = null;
        Instant fromDatetime = null;
        Instant toDatetime = null;

        id = occupancy.getId();
        bedId = occupancy.getBedId();
        encounterId = occupancy.getEncounterId();
        billId = occupancy.getBillId();
        fromDatetime = occupancy.getFromDatetime();
        toDatetime = occupancy.getToDatetime();

        boolean isActive = occupancy.isActive();

        BedOccupancyResponse bedOccupancyResponse = new BedOccupancyResponse( id, bedId, encounterId, billId, fromDatetime, toDatetime, isActive );

        return bedOccupancyResponse;
    }
}
