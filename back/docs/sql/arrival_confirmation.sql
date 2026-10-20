-- Apply before deployment when automatic schema updates are disabled.
ALTER TABLE participant ADD COLUMN IF NOT EXISTS arrived_at DATETIME(6) NULL;

-- Preserve historical confirmations. Their actual confirmation time cannot be reconstructed.
-- Do not backfill arrived_at from the promise time or the current time.
