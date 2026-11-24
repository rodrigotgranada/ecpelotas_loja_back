-- V2__create_store_catalog_kardex_and_invoices.sql
-- Fase 1: Usuários espelho do Portal, Catálogo, Variações (SKU/EAN), Notas de Entrada e Kardex

CREATE TABLE IF NOT EXISTS store_users (
    id VARCHAR(36) PRIMARY KEY,
    portal_user_id VARCHAR(36) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    cpf VARCHAR(20),
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    is_socio_active BOOLEAN NOT NULL DEFAULT FALSE,
    socio_plan_name VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    token_version INT NOT NULL DEFAULT 1,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_users_cpf ON store_users (cpf);

CREATE TABLE IF NOT EXISTS product_categories (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO product_categories (id, name, slug, description, sort_order, active)
VALUES
    ('cat-camisas-jogo', 'Camisas de Jogo', 'camisas-de-jogo', 'Mantos oficiais áureo-cerúleos de jogo e treino.', 1, TRUE),
    ('cat-linha-casual', 'Linha Casual', 'linha-casual', 'Camisetas, polos, moletons e jaquetas do Lobo.', 2, TRUE),
    ('cat-acessorios', 'Acessórios', 'acessorios', 'Bonés, copos, bandeiras, cachecóis e brindes oficiais.', 3, TRUE),
    ('cat-infantil-fem', 'Infantil & Feminino', 'infantil-e-feminino', 'Linha oficial dedicada às crianças e torcedoras do Lobo.', 4, TRUE)
ON CONFLICT (slug) DO NOTHING;

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) REFERENCES product_categories(id),
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    description TEXT,
    brand VARCHAR(100) DEFAULT 'Alcateia / E.C. Pelotas',
    cover_image_url TEXT,
    gallery_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_category_active ON products (category_id, active);

CREATE TABLE IF NOT EXISTS product_variants (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(80) NOT NULL UNIQUE,
    ean_barcode VARCHAR(64),
    size_label VARCHAR(40) NOT NULL,
    color_label VARCHAR(60),
    cost_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    sale_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    member_price NUMERIC(12,2),
    physical_stock INT NOT NULL DEFAULT 0,
    reserved_stock INT NOT NULL DEFAULT 0,
    min_stock INT NOT NULL DEFAULT 2,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_variants_product ON product_variants (product_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variants_ean_not_empty
    ON product_variants (ean_barcode)
    WHERE ean_barcode IS NOT NULL AND ean_barcode <> '';

CREATE TABLE IF NOT EXISTS supplier_sku_mappings (
    id VARCHAR(36) PRIMARY KEY,
    supplier_cnpj VARCHAR(20) NOT NULL,
    supplier_product_code VARCHAR(100) NOT NULL,
    variant_id VARCHAR(36) NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_supplier_sku_mapping UNIQUE (supplier_cnpj, supplier_product_code)
);

CREATE TABLE IF NOT EXISTS stock_documents (
    id VARCHAR(36) PRIMARY KEY,
    doc_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    invoice_number VARCHAR(30),
    invoice_series VARCHAR(15),
    access_key_44 VARCHAR(44) UNIQUE,
    supplier_cnpj VARCHAR(20),
    supplier_name VARCHAR(160),
    xml_url TEXT,
    notes TEXT,
    total_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by_user_id VARCHAR(36),
    posted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stock_documents_status ON stock_documents (status, created_at DESC);

CREATE TABLE IF NOT EXISTS stock_document_items (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES stock_documents(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    supplier_product_code VARCHAR(100),
    ean_barcode VARCHAR(64),
    supplier_product_name VARCHAR(200) NOT NULL,
    ncm VARCHAR(20),
    quantity INT NOT NULL,
    unit_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    variant_id VARCHAR(36) REFERENCES product_variants(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stock_document_items_doc ON stock_document_items (document_id, line_number);

CREATE TABLE IF NOT EXISTS stock_movements (
    id VARCHAR(36) PRIMARY KEY,
    variant_id VARCHAR(36) NOT NULL REFERENCES product_variants(id),
    document_id VARCHAR(36) REFERENCES stock_documents(id),
    order_id VARCHAR(36),
    movement_type VARCHAR(30) NOT NULL,
    quantity_delta INT NOT NULL,
    balance_before INT NOT NULL,
    balance_after INT NOT NULL,
    unit_cost NUMERIC(12,2),
    reason VARCHAR(300),
    actor_user_id VARCHAR(36),
    actor_name VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_variant_created
    ON stock_movements (variant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created
    ON stock_movements (created_at DESC);
