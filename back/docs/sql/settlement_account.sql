-- Apply before deploying when automatic schema updates are disabled.
-- Existing users remain ordinary members. Do not identify or promote accounts by username.
ALTER TABLE site_user
    ADD COLUMN IF NOT EXISTS system_account_type VARCHAR(32) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_system_account_type
    ON site_user (system_account_type);

-- On first startup, a new dedicated account is created if the reserved username is unused.
-- To reuse a verified existing account, configure settlement.account.existing-user-id.
-- This preserves its ID, balance and transaction history, but disables its interactive login.
