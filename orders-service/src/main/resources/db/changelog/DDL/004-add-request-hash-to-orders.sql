--liquibase formatted sql
--changeset sbarry:004-add-request-hash-to-orders

-- Empreinte SHA-256 (hex) du contenu de la requête de création, comparée lors d'un rejeu
-- de la même Idempotency-Key. NULL pour les commandes créées avant ce changeset.
ALTER TABLE orders ADD COLUMN request_hash VARCHAR(64);
--rollback ALTER TABLE orders DROP COLUMN request_hash;
