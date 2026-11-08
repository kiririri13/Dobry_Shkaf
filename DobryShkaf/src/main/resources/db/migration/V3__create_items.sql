CREATE TABLE categories (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    parent_id     UUID         REFERENCES categories (id),
    size_required BOOLEAN      NOT NULL,
    sort_order    INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_code UNIQUE (code)
);

CREATE TABLE districts (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    city       VARCHAR(100) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_districts_city_name UNIQUE (city, name)
);

CREATE TABLE items (
    id             UUID          PRIMARY KEY,
    owner_id       UUID          NOT NULL REFERENCES users (id),
    category_id    UUID          REFERENCES categories (id),
    district_id    UUID          REFERENCES districts (id),
    title          VARCHAR(100)  NOT NULL,
    description    VARCHAR(2000),
    item_condition VARCHAR(32),
    size           VARCHAR(20),
    status         VARCHAR(32)   NOT NULL,
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_items_condition CHECK (item_condition IN ('NEW', 'LIKE_NEW', 'USED', 'NEEDS_CARE')),
    CONSTRAINT chk_items_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RESERVED', 'TRANSFERRED', 'CLOSED'))
);

CREATE INDEX idx_items_status_published_at ON items (status, published_at DESC);
CREATE INDEX idx_items_owner_id ON items (owner_id);
CREATE INDEX idx_items_category_id ON items (category_id);
CREATE INDEX idx_items_district_id ON items (district_id);

CREATE TABLE item_photos (
    id           UUID         PRIMARY KEY,
    item_id      UUID         NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    storage_key  VARCHAR(200) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    sort_order   INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_item_photos_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_item_photos_item_id ON item_photos (item_id);

INSERT INTO categories (code, name, size_required, sort_order) VALUES
    ('kids-clothing', 'Детская одежда', TRUE, 1),
    ('adult-clothing', 'Взрослая одежда', TRUE, 2),
    ('shoes', 'Обувь', TRUE, 3),
    ('toys', 'Игрушки', FALSE, 4),
    ('books', 'Книги', FALSE, 5),
    ('furniture', 'Мебель', FALSE, 6);

INSERT INTO districts (city, name) VALUES
    ('Екатеринбург', 'Академический'),
    ('Екатеринбург', 'Верх-Исетский'),
    ('Екатеринбург', 'Железнодорожный'),
    ('Екатеринбург', 'Кировский'),
    ('Екатеринбург', 'Ленинский'),
    ('Екатеринбург', 'Октябрьский'),
    ('Екатеринбург', 'Орджоникидзевский'),
    ('Екатеринбург', 'Чкаловский');
