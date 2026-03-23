-- Migration: Deduplicate categories
-- For each group of duplicate categories (same user + same name case-insensitive + same parent),
-- keep the oldest record (by created_at) and re-point all references to it, then delete the rest.
-- Also adds a unique constraint to prevent future duplicates.

BEGIN;

-- Step 1: Build a mapping from every duplicate category id -> the canonical id to keep.
-- Duplicates are identified by (user_id, LOWER(name), parent_id) — NULL parent handled via COALESCE.
-- The canonical record is the one with the earliest created_at.

CREATE TEMP TABLE category_id_map AS
SELECT
    dup.id        AS old_id,
    keep.id       AS keep_id
FROM categories dup
JOIN (
    -- For each duplicate group, pick the oldest id as the one to keep
    SELECT DISTINCT ON (user_id, LOWER(name), COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid))
        id,
        user_id,
        LOWER(name) AS name_lower,
        parent_id
    FROM categories
    ORDER BY
        user_id,
        LOWER(name),
        COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid),
        created_at ASC   -- keep oldest
) keep
    ON  keep.user_id = dup.user_id
    AND LOWER(keep.name_lower) = LOWER(dup.name)
    AND COALESCE(keep.parent_id, '00000000-0000-0000-0000-000000000000'::uuid)
      = COALESCE(dup.parent_id,  '00000000-0000-0000-0000-000000000000'::uuid)
    AND keep.id <> dup.id;  -- only the non-canonical duplicates

-- Preview what will be re-mapped (optional — comment out if not needed)
-- SELECT * FROM category_id_map;

-- Step 2: Re-point expenses that reference a duplicate category to the canonical one
UPDATE expenses
SET category_id = m.keep_id
FROM category_id_map m
WHERE expenses.category_id = m.old_id;

-- Step 3: Re-point incomes
UPDATE incomes
SET category_id = m.keep_id
FROM category_id_map m
WHERE incomes.category_id = m.old_id;

-- Step 4: Re-point budgets.
-- A budget has a unique constraint on (user_id, category_id, month), so re-pointing could
-- create a conflict if both the duplicate and canonical budgets exist for the same month.
-- In that case we delete the duplicate budget row first, then re-point the remaining ones.

-- Delete duplicate budget rows where the canonical budget for the same month already exists
DELETE FROM budgets b
USING category_id_map m
WHERE b.category_id = m.old_id
  AND EXISTS (
      SELECT 1 FROM budgets b2
      WHERE b2.user_id   = b.user_id
        AND b2.category_id = m.keep_id
        AND b2.month       = b.month
  );

-- Re-point the remaining budget rows that had no conflict
UPDATE budgets
SET category_id = m.keep_id
FROM category_id_map m
WHERE budgets.category_id = m.old_id;

-- Step 5: Re-point sub-categories whose parent_id points to a duplicate
UPDATE categories
SET parent_id = m.keep_id
FROM category_id_map m
WHERE categories.parent_id = m.old_id;

-- Step 6: Delete the duplicate category rows
DELETE FROM categories
WHERE id IN (SELECT old_id FROM category_id_map);

-- Step 7: Add a unique constraint to prevent future duplicates.
-- Uses two partial indexes because PostgreSQL unique indexes treat NULLs as distinct
-- so a standard unique index on (user_id, LOWER(name), parent_id) won't catch
-- duplicate root categories (parent_id IS NULL).

-- Unique index for root categories (parent_id IS NULL)
CREATE UNIQUE INDEX uq_categories_root_name
    ON categories (user_id, LOWER(name))
    WHERE parent_id IS NULL;

-- Unique index for subcategories (parent_id IS NOT NULL)
CREATE UNIQUE INDEX uq_categories_sub_name
    ON categories (user_id, LOWER(name), parent_id)
    WHERE parent_id IS NOT NULL;

DROP TABLE category_id_map;

COMMIT;
