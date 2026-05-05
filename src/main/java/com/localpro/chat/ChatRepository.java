package com.localpro.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByClientIdAndProviderIdAndListingId(
            UUID clientId, UUID providerId, UUID listingId);

    @Query("""
            SELECT c FROM Chat c
            LEFT JOIN FETCH c.client
            LEFT JOIN FETCH c.provider
            LEFT JOIN FETCH c.listing
            WHERE c.id = :id
            """)
    Optional<Chat> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT c FROM Chat c
            LEFT JOIN FETCH c.client
            LEFT JOIN FETCH c.provider
            LEFT JOIN FETCH c.listing
            WHERE c.client.id = :userId OR c.provider.id = :userId
            ORDER BY c.lastMessageAt DESC NULLS LAST
            """)
    List<Chat> findAllByUserIdWithDetails(@Param("userId") UUID userId);
}
