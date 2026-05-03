package com.localpro.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByClientIdAndProviderIdAndListingId(
            UUID clientId, UUID providerId, UUID listingId);

    List<Chat> findByClientIdOrProviderIdOrderByLastMessageAtDesc(
            UUID clientId, UUID providerId);
}
