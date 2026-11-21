ALTER TABLE users ADD COLUMN district_id UUID REFERENCES districts (id);
ALTER TABLE users ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('USER', 'VOLUNTEER', 'ORGANIZER', 'ADMIN', 'MODERATOR'));

ALTER TABLE items ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (moderation_status IN ('PENDING', 'REVIEWED', 'HIDDEN'));

CREATE TABLE favorite_items (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_id)
);

CREATE TABLE favorite_points (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    point_id UUID NOT NULL REFERENCES collection_points (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, point_id)
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    author_id UUID NOT NULL REFERENCES users (id),
    target_user_id UUID NOT NULL REFERENCES users (id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text VARCHAR(1000),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (transfer_id, author_id)
);

CREATE INDEX idx_reviews_target ON reviews (target_user_id, created_at DESC) WHERE NOT hidden;

CREATE TABLE complaints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users (id),
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('ITEM', 'POINT', 'PROFILE', 'MESSAGE', 'CHAT')),
    target_id UUID NOT NULL,
    chat_id UUID REFERENCES chats (id),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    outcome VARCHAR(16) CHECK (outcome IN ('UPHELD', 'DISMISSED')),
    explanation VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((target_type IN ('MESSAGE', 'CHAT')) = (chat_id IS NOT NULL)),
    CHECK ((status = 'CLOSED') = (outcome IS NOT NULL AND explanation IS NOT NULL))
);

CREATE INDEX idx_complaints_status_created ON complaints (status, created_at DESC);

CREATE TABLE moderation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES users (id),
    target_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    complaint_id UUID REFERENCES complaints (id),
    outcome VARCHAR(16) CHECK (outcome IN ('UPHELD', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_moderation_log_created ON moderation_log (created_at DESC);

CREATE TABLE eco_coefficients (
    category VARCHAR(50) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    coefficient NUMERIC(10,4) NOT NULL CHECK (coefficient > 0),
    source TEXT NOT NULL,
    PRIMARY KEY (category, version)
);

INSERT INTO eco_coefficients (category, version, coefficient, source) VALUES
    ('COTTON_TSHIRT', 1, 5.2, 'https://impactco2.fr/outils/habillement/tshirtencoton'),
    ('POLYESTER_TSHIRT', 1, 5.5, 'https://impactco2.fr/outils/habillement/tshirtenpolyester'),
    ('JEANS', 1, 23.2, 'https://impactco2.fr/outils/habillement/jeans'),
    ('COTTON_SHIRT', 1, 11.2, 'https://impactco2.fr/outils/habillement/chemiseencoton');

CREATE TABLE eco_item_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    origin_line_id UUID REFERENCES eco_item_lines (id),
    category VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    weight_kg NUMERIC(14,3) CHECK (weight_kg > 0),
    UNIQUE (item_id, origin_line_id)
);

CREATE TABLE eco_events (
    item_id UUID PRIMARY KEY REFERENCES items (id),
    author_id UUID NOT NULL REFERENCES users (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    credited_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eco_credits (
    line_id UUID PRIMARY KEY REFERENCES eco_item_lines (id),
    item_id UUID NOT NULL REFERENCES eco_events (item_id),
    origin_line_id UUID NOT NULL REFERENCES eco_item_lines (id),
    category VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    weight_kg NUMERIC(14,3),
    coefficient NUMERIC(10,4),
    source TEXT,
    coefficient_version INTEGER,
    co2e_kg NUMERIC(18,4),
    counted BOOLEAN NOT NULL
);

CREATE UNIQUE INDEX uq_eco_counted_origin ON eco_credits (origin_line_id) WHERE counted;
CREATE INDEX idx_eco_credits_item ON eco_credits (item_id);
CREATE INDEX idx_eco_events_author ON eco_events (author_id);

CREATE TABLE eco_point_batches (
    id UUID PRIMARY KEY,
    point_id UUID NOT NULL REFERENCES collection_points (id),
    author_id UUID NOT NULL REFERENCES users (id),
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('CONTAINER_COLLECTION', 'CONFIRMED_DISTRIBUTION')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    weight_kg NUMERIC(14,3) NOT NULL CHECK (weight_kg > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eco_recalculations (
    line_id UUID NOT NULL REFERENCES eco_credits (line_id),
    coefficient_version INTEGER NOT NULL,
    coefficient NUMERIC(10,4) NOT NULL,
    source TEXT NOT NULL,
    co2e_kg NUMERIC(18,4) NOT NULL,
    actor_id UUID NOT NULL REFERENCES users (id),
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (line_id, coefficient_version)
);
