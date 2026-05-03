CREATE TABLE service_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10,2),
    price_type VARCHAR(20) DEFAULT 'FROM',
    location GEOGRAPHY(POINT, 4326),
    address TEXT,
    city VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    view_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE service_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES service_listings(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INT DEFAULT 0
);

CREATE INDEX idx_listings_location ON service_listings USING GIST (location);
CREATE INDEX idx_listings_provider ON service_listings (provider_id);
CREATE INDEX idx_listings_category ON service_listings (category_id);
CREATE INDEX idx_listings_status ON service_listings (status);

CREATE TRIGGER listings_updated_at
    BEFORE UPDATE ON service_listings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
