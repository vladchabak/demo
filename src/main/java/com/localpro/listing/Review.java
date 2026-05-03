package com.localpro.listing;

import com.localpro.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"listing", "client"})
@EqualsAndHashCode(exclude = {"listing", "client"})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private ServiceListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(nullable = false)
    private Integer rating;

    private String comment;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
