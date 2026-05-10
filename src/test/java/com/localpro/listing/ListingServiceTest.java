package com.localpro.listing;

import com.localpro.listing.dto.ListingRequest;
import com.localpro.listing.dto.UpdateListingRequest;
import com.localpro.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock ServiceListingRepository listingRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ListingMapper listingMapper;
    @InjectMocks ListingService listingService;

    UUID providerId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID listingId = UUID.randomUUID();

    User provider;
    Category category;
    ServiceListing listing;

    @BeforeEach
    void setUp() {
        provider = User.builder()
                .id(providerId)
                .firebaseUid("prov-uid")
                .email("provider@test.com")
                .name("Provider")
                .build();

        category = Category.builder()
                .id(categoryId)
                .name("Cleaning")
                .build();

        listing = ServiceListing.builder()
                .id(listingId)
                .provider(provider)
                .category(category)
                .title("Test Service")
                .viewCount(0)
                .build();
    }

    @Test
    void create_validRequest_savesListing() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListingRequest req = new ListingRequest(
                "House Cleaning", "Deep clean", categoryId,
                BigDecimal.valueOf(50), PriceType.PER_SERVICE, 50.45, 30.52, "Main St", "Kyiv", null, null);

        ServiceListing result = listingService.create(provider, req);

        assertThat(result.getTitle()).isEqualTo("House Cleaning");
        assertThat(result.getProvider()).isEqualTo(provider);
        assertThat(result.getLocation()).isNotNull();
        verify(listingRepository, atLeastOnce()).save(any());
    }

    @Test
    void create_categoryNotFound_throwsEntityNotFoundException() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ListingRequest req = new ListingRequest(
                "Title", null, categoryId, null, null, 50.0, 30.0, null, null, null, null);

        assertThatThrownBy(() -> listingService.create(provider, req))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_ownListing_appliesChanges() {
        when(listingRepository.findByIdAndProviderId(listingId, providerId))
                .thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        UpdateListingRequest req = new UpdateListingRequest(
                "Updated Title", null, null, null, null, null, null, null, null, null);

        ServiceListing result = listingService.update(providerId, listingId, req);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        verify(listingRepository).save(listing);
    }

    @Test
    void update_notOwner_throwsEntityNotFoundException() {
        when(listingRepository.findByIdAndProviderId(listingId, providerId))
                .thenReturn(Optional.empty());

        UpdateListingRequest req = new UpdateListingRequest(
                "Title", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> listingService.update(providerId, listingId, req))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_ownListing_setsDeletedStatus() {
        when(listingRepository.findByIdAndProviderId(listingId, providerId))
                .thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        listingService.delete(providerId, listingId);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
        verify(listingRepository).save(listing);
    }

    @Test
    void delete_notOwner_throwsEntityNotFoundException() {
        when(listingRepository.findByIdAndProviderId(listingId, providerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.delete(providerId, listingId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getById_existingListing_incrementsViewCountAndSaves() {
        listing.setViewCount(5);
        when(listingRepository.findByIdWithDetails(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        ServiceListing result = listingService.getById(listingId);

        assertThat(result.getViewCount()).isEqualTo(6);
        verify(listingRepository).save(listing);
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(listingRepository.findByIdWithDetails(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.getById(listingId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getByProvider_returnsPaginatedResults() {
        Page<ServiceListing> page = new PageImpl<>(List.of(listing));
        when(listingRepository.findByProviderIdAndStatusNot(eq(providerId), eq(ListingStatus.DELETED), any()))
                .thenReturn(page);

        Page<ServiceListing> result = listingService.getByProvider(providerId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }
}
