ALTER TABLE users ADD COLUMN bio VARCHAR(1000);

CREATE INDEX idx_item_photos_display_order ON item_photos (item_id, sort_order, id);
