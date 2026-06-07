-- Migration: Add receipt_path column to expenses table
-- Stores the server-side filename of an expense's attached receipt image.
-- Nullable: existing expenses have no receipt. The application serves the file
-- via GET /expenses/{id}/receipt; only the filename is persisted here.
--
-- Run this against prod BEFORE deploying the receipt-attachments build, since
-- the prod profile uses spring.jpa.hibernate.ddl-auto=validate (Hibernate will
-- refuse to start if the entity field has no matching column). In dev,
-- ddl-auto=update adds the column automatically and this script is unnecessary.

ALTER TABLE expenses
ADD COLUMN IF NOT EXISTS receipt_path VARCHAR(512);

-- Verification query (optional)
-- SELECT id, name, receipt_path FROM expenses WHERE receipt_path IS NOT NULL;
