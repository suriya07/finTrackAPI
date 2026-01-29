-- Migration: Add type column to categories table
-- This script adds the type column to differentiate between INCOME, EXPENSE, and BUDGET categories

-- Step 1: Add the type column as nullable first (to handle existing data)
ALTER TABLE categories 
ADD COLUMN type VARCHAR(20);

-- Step 2: Set a default value for existing rows (assuming they are EXPENSE categories)
-- You may want to adjust this based on your actual data
UPDATE categories 
SET type = 'EXPENSE' 
WHERE type IS NULL;

-- Step 3: Make the column NOT NULL
ALTER TABLE categories 
ALTER COLUMN type SET NOT NULL;

-- Step 4: Add the CHECK constraint
ALTER TABLE categories 
ADD CONSTRAINT chk_category_type 
CHECK (type IN ('EXPENSE', 'INCOME', 'BUDGET'));

-- Step 5: Create an index on the type column for better query performance
CREATE INDEX idx_categories_type ON categories(type);

-- Verification query (optional - comment out if not needed)
-- SELECT id, name, type, parent_id FROM categories ORDER BY type, name;
