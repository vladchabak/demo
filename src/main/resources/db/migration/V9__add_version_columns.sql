-- Optimistic locking: version columns required by @Version JPA annotation on User and ServiceListing
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE service_listings ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
