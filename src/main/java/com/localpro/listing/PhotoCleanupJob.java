package com.localpro.listing;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoCleanupJob {

    private static final String FOLDER_PREFIX = "localpro/listings/";

    private final ServicePhotoRepository photoRepository;
    private final Cloudinary cloudinary;

    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanup() {
        Set<String> dbPublicIds = photoRepository.findAll().stream()
                .map(p -> extractPublicId(p.getUrl()))
                .collect(Collectors.toSet());

        List<String> cloudinaryPublicIds = fetchAllCloudinaryPublicIds();

        int deleted = 0;
        for (String publicId : cloudinaryPublicIds) {
            if (!dbPublicIds.contains(publicId)) {
                try {
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                    deleted++;
                } catch (Exception e) {
                    log.warn("Failed to destroy Cloudinary asset {}: {}", publicId, e.getMessage());
                }
            }
        }

        log.info("Deleted {} orphaned Cloudinary assets", deleted);
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchAllCloudinaryPublicIds() {
        List<String> publicIds = new ArrayList<>();
        String nextCursor = null;

        do {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("type", "upload");
                params.put("prefix", FOLDER_PREFIX);
                params.put("max_results", 500);
                if (nextCursor != null) {
                    params.put("next_cursor", nextCursor);
                }

                Map<?, ?> result = cloudinary.api().resources(params);
                List<Map<?, ?>> resources = (List<Map<?, ?>>) result.get("resources");
                if (resources != null) {
                    for (Map<?, ?> resource : resources) {
                        publicIds.add((String) resource.get("public_id"));
                    }
                }
                nextCursor = (String) result.get("next_cursor");
            } catch (Exception e) {
                log.error("Failed to list Cloudinary assets: {}", e.getMessage());
                break;
            }
        } while (nextCursor != null);

        return publicIds;
    }

    private String extractPublicId(String url) {
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
