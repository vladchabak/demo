package com.localpro.listing;

import com.localpro.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"provider", "category", "photos"})
@EqualsAndHashCode(exclude = {"provider", "category", "photos"})
public class ServiceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    private String description;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type")
    @Builder.Default
    private PriceType priceType = PriceType.FROM;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    private String address;
    private String city;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ServicePhoto> photos = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
