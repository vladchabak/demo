package com.localpro.chat;

import com.localpro.listing.ServiceListing;
import com.localpro.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"client", "provider", "listing"})
@EqualsAndHashCode(exclude = {"client", "provider", "listing"})
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private ServiceListing listing;

    @Column(name = "last_message")
    private String lastMessage;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
