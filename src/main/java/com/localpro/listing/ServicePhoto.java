package com.localpro.listing;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "service_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "listing")
@EqualsAndHashCode(exclude = "listing")
public class ServicePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private ServiceListing listing;

    @Column(nullable = false)
    private String url;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
