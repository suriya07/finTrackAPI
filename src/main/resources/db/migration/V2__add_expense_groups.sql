-- Expense groups: user-defined groupings (e.g. a trip or event) that roll up
-- spending across categories and accounts. Expenses reference a group via an
-- optional expenses.group_id (ON nothing — the app un-assigns members before
-- deleting a group).
--
-- Column types mirror exactly what Hibernate generates for the audited entities
-- (uuid keys, TEXT name/description, timestamptz audit columns) so this schema
-- passes `spring.jpa.hibernate.ddl-auto=validate` in production.
--
-- IF [NOT] EXISTS guards make the migration safe to run on a dev database whose
-- tables were already created by ddl-auto=update.

CREATE TABLE IF NOT EXISTS expense_groups (
    id          uuid NOT NULL,
    user_id     uuid NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    created_at  timestamp(6) with time zone NOT NULL,
    created_by  uuid,
    updated_at  timestamp(6) with time zone,
    updated_by  uuid,
    PRIMARY KEY (id),
    CONSTRAINT fk_expense_groups_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_expense_groups_user ON expense_groups (user_id);

-- Optional link from an expense to its group.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS group_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_group') THEN
        ALTER TABLE expenses
            ADD CONSTRAINT fk_expenses_group
            FOREIGN KEY (group_id) REFERENCES expense_groups (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses (group_id);
