ALTER TABLE service_listings
    ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN verified_at TIMESTAMP,
    ADD COLUMN custom_questions TEXT,
    ADD COLUMN is_visible_on_map BOOLEAN NOT NULL DEFAULT false;

-- Create index for the new is_visible_on_map column for better query performance
CREATE INDEX idx_listings_visible_on_map ON service_listings(is_visible_on_map);

-- Migrate old price_type values to new enum names
UPDATE service_listings SET price_type = 'PER_SERVICE' WHERE price_type IN ('FIXED', 'FROM');
UPDATE service_listings SET price_type = 'PER_HOUR' WHERE price_type = 'HOURLY';
