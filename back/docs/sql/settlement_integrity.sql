-- MariaDB: deploy before the updated application. Existing records remain untouched.
ALTER TABLE promise ADD COLUMN IF NOT EXISTS settlement_result TEXT NULL;
ALTER TABLE money_record ADD COLUMN IF NOT EXISTS promise_id BIGINT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_record_promise_user_direction
    ON money_record (promise_id, user_id, is_penalty);
-- Do not infer historical promise IDs from titles: titles are not unique.
-- MariaDB allows multiple NULL promise_id values, so legacy records are preserved.
