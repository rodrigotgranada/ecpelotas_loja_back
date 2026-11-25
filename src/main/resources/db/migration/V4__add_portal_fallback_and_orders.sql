-- V4__add_portal_fallback_and_orders.sql
-- Fase 2: Cache/Fallback de login do Portal em store_users, Configurações da Loja, Pedidos/Reservas WhatsApp e Pagamentos Múltiplos

ALTER TABLE store_users
    ADD COLUMN IF NOT EXISTS portal_verifier_hash VARCHAR(120);

CREATE TABLE IF NOT EXISTS store_settings (
    id VARCHAR(36) PRIMARY KEY,
    store_name VARCHAR(120) NOT NULL DEFAULT 'Loja Oficial Alcateia',
    whatsapp_number VARCHAR(30) NOT NULL DEFAULT '5553999999999',
    open_hour VARCHAR(5) NOT NULL DEFAULT '09:00',
    close_hour VARCHAR(5) NOT NULL DEFAULT '19:00',
    working_days VARCHAR(60) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT',
    reservation_ttl_minutes INT NOT NULL DEFAULT 180,
    max_open_reservations_per_user INT NOT NULL DEFAULT 2,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO store_settings (
    id, store_name, whatsapp_number, open_hour, close_hour, working_days,
    reservation_ttl_minutes, max_open_reservations_per_user
) VALUES (
    'default', 'Loja Oficial Alcateia', '5553999999999', '09:00', '19:00',
    'MON,TUE,WED,THU,FRI,SAT', 180, 2
) ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    order_code VARCHAR(24) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    customer_type VARCHAR(30) NOT NULL,
    store_user_id VARCHAR(36) REFERENCES store_users(id),
    portal_user_id VARCHAR(36),
    customer_name VARCHAR(150) NOT NULL,
    customer_whatsapp VARCHAR(30),
    customer_cpf VARCHAR(20),
    is_socio_discount_applied BOOLEAN NOT NULL DEFAULT FALSE,
    subtotal_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes TEXT,
    whatsapp_summary_text TEXT,
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancelled_reason VARCHAR(255),
    seller_user_id VARCHAR(36),
    seller_name VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_portal_user ON orders (portal_user_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_cpf ON orders (customer_cpf);
CREATE INDEX IF NOT EXISTS idx_orders_status_expires ON orders (status, expires_at);

CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id VARCHAR(36) NOT NULL REFERENCES product_variants(id),
    product_name VARCHAR(160) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    size_label VARCHAR(40) NOT NULL,
    color_label VARCHAR(60),
    quantity INT NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    applied_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);

CREATE TABLE IF NOT EXISTS order_payments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    payment_method VARCHAR(30) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_payments_order ON order_payments (order_id);

CREATE TABLE IF NOT EXISTS order_events (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(36),
    actor_name VARCHAR(150),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_events_order ON order_events (order_id);
