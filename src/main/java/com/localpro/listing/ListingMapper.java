package com.localpro.listing;

import com.localpro.listing.dto.ListingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ListingMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "providerId", source = "provider.id")
    @Mapping(target = "providerName", source = "provider.name")
    @Mapping(target = "providerAvatarUrl", source = "provider.avatarUrl")
    @Mapping(target = "providerRating", source = "provider.rating")
    @Mapping(target = "photoUrls", source = "photos")
    ListingResponse toResponse(ServiceListing listing);

    default List<String> photosToUrls(List<ServicePhoto> photos) {
        if (photos == null) return List.of();
        return photos.stream().map(ServicePhoto::getUrl).toList();
    }
}
