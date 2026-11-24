-- V1__init_loja_schema.sql
-- Estrutura inicial da Loja Oficial Alcateia (E.C. Pelotas)

CREATE TABLE IF NOT EXISTS store_settings (
    id BIGSERIAL PRIMARY KEY,
    store_name VARCHAR(150) NOT NULL DEFAULT 'Loja Oficial Alcateia',
    whatsapp_number VARCHAR(30) NOT NULL DEFAULT '5553999999999',
    reservation_business_hours_ttl_minutes INTEGER NOT NULL DEFAULT 180,
    max_open_reservations_per_phone INTEGER NOT NULL DEFAULT 2,
    business_open_time VARCHAR(5) NOT NULL DEFAULT '09:00',
    business_close_time VARCHAR(5) NOT NULL DEFAULT '19:00',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO store_settings (store_name)
SELECT 'Loja Oficial Alcateia'
WHERE NOT EXISTS (SELECT 1 FROM store_settings);
