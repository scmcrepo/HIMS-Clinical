package com.hms.infrastructure.mapper;

import com.hms.api.bed.response.BedOccupancyResponse;
import com.hms.api.bed.response.BedResponse;
import com.hms.domain.bed.model.Bed;
import com.hms.domain.bed.model.BedOccupancy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BedMapper {

    @Mapping(target = "isActive", expression = "java(occupancy.isActive())")
    BedOccupancyResponse toOccupancyResponse(BedOccupancy occupancy);
}
