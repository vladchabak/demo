package com.localpro.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServicePhotoRepository extends JpaRepository<ServicePhoto, UUID> {
}
