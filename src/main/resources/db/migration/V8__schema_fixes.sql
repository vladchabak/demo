-- Fix 1: Drop duplicate GiST index (V2 already created idx_listings_location on same column)
DROP INDEX IF EXISTS idx_listings_location;

-- Fix 2: TIMESTAMP → TIMESTAMPTZ for timezone-aware columns
-- scheduled_at stores booking appointment times — must be timezone-aware for multi-timezone users
ALTER TABLE bookings ALTER COLUMN scheduled_at TYPE TIMESTAMPTZ;
-- verified_at stores admin verification time — same concern
ALTER TABLE service_listings ALTER COLUMN verified_at TYPE TIMESTAMPTZ;

-- Fix 3: Replace UNIQUE constraint that doesn't protect NULL listing_id
-- In PostgreSQL, UNIQUE treats NULLs as distinct, so (client, provider, NULL) rows are not
-- considered duplicates, allowing duplicate direct-message chats between the same two users.
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_client_id_provider_id_listing_id_key;

-- Unique constraint for chats linked to a specific listing
CREATE UNIQUE INDEX IF NOT EXISTS chats_unique_with_listing
    ON chats (client_id, provider_id, listing_id)
    WHERE listing_id IS NOT NULL;

-- Unique constraint for direct chats with no listing
CREATE UNIQUE INDEX IF NOT EXISTS chats_unique_without_listing
    ON chats (client_id, provider_id)
    WHERE listing_id IS NULL;
