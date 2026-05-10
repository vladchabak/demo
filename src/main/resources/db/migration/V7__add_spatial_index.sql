-- Add spatial index for location-based queries
CREATE INDEX IF NOT EXISTS idx_service_listings_location
    ON service_listings USING GIST(location);

-- Add index for visible listings (used in findNearby queries)
CREATE INDEX IF NOT EXISTS idx_service_listings_visible
    ON service_listings(is_visible_on_map) WHERE is_visible_on_map = true;

-- Add version column for optimistic locking
ALTER TABLE service_listings ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
