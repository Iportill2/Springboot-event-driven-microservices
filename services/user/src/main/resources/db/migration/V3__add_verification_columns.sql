ALTER TABLE users ADD COLUMN verif_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN verif_token_expires_at TIMESTAMP;