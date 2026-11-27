-- Оценка по аналогии с хлопковыми джинсами: 23,2 кг CO₂-экв. × 0,7 кг / 0,8 кг.
INSERT INTO eco_coefficients (category, version, coefficient, source) VALUES
    ('COTTON_TROUSERS', 1, 20.3000, 'https://bilans-ges.ademe.fr/docutheque/guide-sectoriel-filiere-mode-et-textile.ext');

-- Добавляем первую оценку к ранее учтённым позициям, у которых её ещё нет.
UPDATE eco_credits c
SET coefficient = k.coefficient,
    source = k.source,
    coefficient_version = k.version,
    co2e_kg = c.quantity * k.coefficient
FROM eco_coefficients k
WHERE c.category = 'COTTON_TROUSERS' AND k.category = c.category AND k.version = 1
    AND c.coefficient_version IS NULL AND c.co2e_kg IS NULL;
