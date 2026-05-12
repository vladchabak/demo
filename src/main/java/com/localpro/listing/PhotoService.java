package com.localpro.listing;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.localpro.listing.dto.PhotoResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final Cloudinary cloudinary;
    private final ServiceListingRepository listingRepository;
    private final ServicePhotoRepository photoRepository;

    @Transactional
    public PhotoResponse upload(UUID providerId, UUID listingId, MultipartFile file) {
        ServiceListing listing = listingRepository.findByIdAndProviderId(listingId, providerId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));

        Map<?, ?> uploadResult;
        try {
            uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "localpro/listings/" + listingId)
            );
        } catch (IOException e) {
            throw new RuntimeException("Cloudinary upload failed", e);
        }

        String secureUrl = (String) uploadResult.get("secure_url");
        int nextOrder = listing.getPhotos().size();

        ServicePhoto photo = ServicePhoto.builder()
                .listing(listing)
                .url(secureUrl)
                .sortOrder(nextOrder)
                .build();
        photoRepository.save(photo);

        log.info("Uploaded photo {} for listing {}", photo.getId(), listingId);
        return new PhotoResponse(photo.getId(), photo.getUrl(), photo.getSortOrder());
    }

    @Transactional
    public void delete(UUID providerId, UUID listingId, UUID photoId) {
        listingRepository.findByIdAndProviderId(listingId, providerId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));

        ServicePhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("Photo not found: " + photoId));

        String publicId = extractPublicId(photo.getUrl());
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Cloudinary destroy failed for publicId {}: {}", publicId, e.getMessage());
        }

        photoRepository.delete(photo);
        log.info("Deleted photo {} from listing {}", photoId, listingId);
    }

    @Transactional
    public List<PhotoResponse> updateOrder(UUID providerId, UUID listingId, List<UUID> photoIds) {
        ServiceListing listing = listingRepository.findByIdAndProviderId(listingId, providerId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));

        List<ServicePhoto> photos = listing.getPhotos();
        for (int i = 0; i < photoIds.size(); i++) {
            UUID targetId = photoIds.get(i);
            int order = i;
            photos.stream()
                    .filter(p -> p.getId().equals(targetId))
                    .findFirst()
                    .ifPresent(p -> p.setSortOrder(order));
        }

        return photos.stream()
                .sorted(java.util.Comparator.comparingInt(ServicePhoto::getSortOrder))
                .map(p -> new PhotoResponse(p.getId(), p.getUrl(), p.getSortOrder()))
                .toList();
    }

    private String extractPublicId(String url) {
        // https://res.cloudinary.com/cloud/image/upload/v1234567890/localpro/listings/uuid/file.jpg
        // → localpro/listings/uuid/file
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx == -1) return url;
        String after = url.substring(uploadIdx + 8);
        if (after.matches("v\\d+/.*")) {
            after = after.substring(after.indexOf('/') + 1);
        }
        int dotIdx = after.lastIndexOf('.');
        return dotIdx != -1 ? after.substring(0, dotIdx) : after;
    }
}
