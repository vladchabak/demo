package com.localpro.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatingReconciliationJob {

    private final ReviewRepository reviewRepository;
    private final ServiceListingRepository serviceListingRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void reconcile() {
        List<Object[]> stats = reviewRepository.findRatingStatsByListing();
        for (Object[] row : stats) {
            UUID listingId = (UUID) row[0];
            int count = ((Number) row[1]).intValue();
            BigDecimal avgRating = BigDecimal.valueOf((Double) row[2])
                    .setScale(2, RoundingMode.HALF_UP);
            serviceListingRepository.updateRatingStats(listingId, count, avgRating);
        }
        log.info("Reconciled {} listings", stats.size());
    }
}
