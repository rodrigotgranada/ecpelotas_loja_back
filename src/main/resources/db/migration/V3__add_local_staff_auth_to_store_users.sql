-- V3__add_local_staff_auth_to_store_users.sql
-- Permite perfis e contas de funcionários exclusivos da Loja (sem necessidade de conta no Portal)

ALTER TABLE store_users
    ALTER COLUMN portal_user_id DROP NOT NULL;

ALTER TABLE store_users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(120),
    ADD COLUMN IF NOT EXISTS auth_source VARCHAR(20) NOT NULL DEFAULT 'PORTAL';

CREATE UNIQUE INDEX IF NOT EXISTS uq_store_users_email_lower
    ON store_users (LOWER(email))
    WHERE email IS NOT NULL AND email <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_store_users_cpf_clean
    ON store_users (cpf)
    WHERE cpf IS NOT NULL AND cpf <> '';
