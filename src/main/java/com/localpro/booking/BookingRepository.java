package com.localpro.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.listing
            JOIN FETCH b.customer
            JOIN FETCH b.provider
            WHERE b.customer.id = :userId OR b.provider.id = :userId
            ORDER BY b.createdAt DESC
            """)
    List<Booking> findAllByUserIdWithDetails(@Param("userId") UUID userId);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.listing
            JOIN FETCH b.customer
            JOIN FETCH b.provider
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.customer.id = :customerId
              AND b.listing.id = :listingId
              AND b.scheduledAt = :scheduledAt
              AND b.status IN (com.localpro.booking.BookingStatus.PENDING,
                               com.localpro.booking.BookingStatus.CONFIRMED)
            """)
    boolean existsActiveBooking(
            @Param("customerId") UUID customerId,
            @Param("listingId") UUID listingId,
            @Param("scheduledAt") Instant scheduledAt);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.provider.id = :providerId
              AND b.scheduledAt = :scheduledAt
              AND b.status = com.localpro.booking.BookingStatus.CONFIRMED
            """)
    boolean existsProviderConflict(
            @Param("providerId") UUID providerId,
            @Param("scheduledAt") Instant scheduledAt);

    @EntityGraph(attributePaths = {"listing", "customer", "provider"})
    @Query(value = """
            SELECT b FROM Booking b
            WHERE (b.customer.id = :userId OR b.provider.id = :userId)
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM Booking b
            WHERE (b.customer.id = :userId OR b.provider.id = :userId)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<Booking> findPageByUserIdWithDetails(
            @Param("userId") UUID userId,
            @Param("status") BookingStatus status,
            Pageable pageable);
}
