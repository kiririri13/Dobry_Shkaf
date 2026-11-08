CREATE TABLE organizations (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_organizations_name_lower ON organizations (lower(name));

CREATE TABLE collection_points (
    id                   UUID          PRIMARY KEY,
    organization_id      UUID          NOT NULL REFERENCES organizations (id),
    district_id          UUID          NOT NULL REFERENCES districts (id),
    point_type           VARCHAR(32)   NOT NULL,
    name                 VARCHAR(200)  NOT NULL,
    address              VARCHAR(300)  NOT NULL,
    latitude             NUMERIC(9, 6) NOT NULL,
    longitude            NUMERIC(9, 6) NOT NULL,
    hours                VARCHAR(500)  NOT NULL,
    contacts             VARCHAR(500)  NOT NULL,
    conditions           VARCHAR(2000) NOT NULL,
    receiving_conditions VARCHAR(2000),
    source               VARCHAR(300)  NOT NULL,
    verified_at          DATE          NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_collection_points_type CHECK (point_type IN ('EXCHANGE', 'COLLECTION')),
    CONSTRAINT chk_collection_points_status CHECK (status IN ('ACTIVE', 'TEMPORARILY_CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_collection_points_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_collection_points_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_collection_points_receiving_conditions
        CHECK (point_type <> 'EXCHANGE' OR receiving_conditions IS NOT NULL)
);

CREATE INDEX idx_collection_points_status ON collection_points (status);
CREATE INDEX idx_collection_points_district_id ON collection_points (district_id);
CREATE INDEX idx_collection_points_organization_id ON collection_points (organization_id);

CREATE TABLE collection_point_categories (
    point_id    UUID NOT NULL REFERENCES collection_points (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id),
    PRIMARY KEY (point_id, category_id)
);

CREATE INDEX idx_collection_point_categories_category_id ON collection_point_categories (category_id);
