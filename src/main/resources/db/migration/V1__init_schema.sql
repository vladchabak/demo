-- Enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    bio TEXT,
    phone VARCHAR(30),
    rating NUMERIC(3,2) DEFAULT 0,
    review_count INT DEFAULT 0,
    fcm_token VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Auto-update updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50),
    parent_id UUID REFERENCES categories(id),
    sort_order INT DEFAULT 0
);

-- Seed categories
INSERT INTO categories (name, icon, sort_order) VALUES
    ('Cleaning', 'cleaning', 1),
    ('Plumbing', 'plumbing', 2),
    ('Tutoring', 'tutoring', 3),
    ('Beauty & Wellness', 'beauty', 4),
    ('Repairs', 'repairs', 5),
    ('Moving', 'moving', 6),
    ('Photography', 'photo', 7),
    ('IT & Tech', 'tech', 8);
