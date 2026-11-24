ALTER TABLE items ADD COLUMN delivery_method VARCHAR(16) NOT NULL DEFAULT 'FREE'
    CHECK (delivery_method IN ('FREE', 'EXCHANGE'));

ALTER TABLE users ADD COLUMN avatar_key VARCHAR(200);
ALTER TABLE users ADD COLUMN avatar_content_type VARCHAR(50);
ALTER TABLE users ADD CONSTRAINT chk_avatar_metadata
    CHECK ((avatar_key IS NULL) = (avatar_content_type IS NULL));

ALTER TABLE messages ADD COLUMN attachment_key VARCHAR(200);
ALTER TABLE messages ADD COLUMN attachment_name VARCHAR(255);
ALTER TABLE messages ADD COLUMN attachment_size_bytes INTEGER;
ALTER TABLE messages ADD CONSTRAINT chk_message_attachment CHECK (
    (attachment_key IS NULL AND attachment_name IS NULL AND attachment_size_bytes IS NULL)
    OR (attachment_key IS NOT NULL AND attachment_name IS NOT NULL
        AND attachment_size_bytes IS NOT NULL AND attachment_size_bytes BETWEEN 1 AND 5242880)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    detail VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
CREATE UNIQUE INDEX uq_review_notification ON notifications (entity_id) WHERE type = 'REVIEW_RECEIVED';

INSERT INTO notifications (user_id, type, entity_id, created_at)
SELECT target_user_id, 'REVIEW_RECEIVED', id, created_at FROM reviews WHERE NOT hidden;
