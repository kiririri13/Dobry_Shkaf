CREATE TABLE eco_item_types (
    category VARCHAR(50) PRIMARY KEY,
    name TEXT NOT NULL,
    average_weight_kg NUMERIC(8,3) NOT NULL CHECK (average_weight_kg > 0)
);

INSERT INTO eco_item_types (category, name, average_weight_kg) VALUES
    ('COTTON_TSHIRT', 'Хлопковая футболка', 0.300),
    ('POLYESTER_TSHIRT', 'Полиэстеровая футболка', 0.200),
    ('JEANS', 'Джинсы', 0.700),
    ('COTTON_SHIRT', 'Хлопковая рубашка', 0.300),
    ('COTTON_TROUSERS', 'Хлопковые штаны', 0.700);

UPDATE eco_item_lines l
SET weight_kg = l.quantity * t.average_weight_kg
FROM eco_item_types t
WHERE t.category = l.category AND l.weight_kg IS NULL AND l.origin_line_id IS NULL
    AND NOT EXISTS (SELECT 1 FROM eco_events e WHERE e.item_id = l.item_id);
