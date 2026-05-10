package com.localpro.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
