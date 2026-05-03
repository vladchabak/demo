package com.localpro.listing;

import com.localpro.listing.dto.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.name")
    @Mapping(target = "clientAvatarUrl", source = "client.avatarUrl")
    ReviewResponse toResponse(Review review);
}
