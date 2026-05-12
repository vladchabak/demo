-- Add cancellation reason field
ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT;

-- Prevent duplicate active bookings (same customer, listing, time)
CREATE UNIQUE INDEX idx_bookings_no_duplicate_active
    ON bookings (customer_id, listing_id, scheduled_at)
    WHERE status IN ('PENDING', 'CONFIRMED');

-- Speed up provider conflict check (same provider, same time, confirmed status)
CREATE INDEX idx_bookings_provider_scheduled
    ON bookings (provider_id, scheduled_at)
    WHERE status = 'CONFIRMED';
