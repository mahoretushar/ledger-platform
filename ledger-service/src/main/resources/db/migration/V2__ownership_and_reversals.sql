-- Every account belongs to a login; existing rows are assigned to the admin user.
ALTER TABLE account ADD COLUMN owner_username VARCHAR(64);
UPDATE account SET owner_username = 'admin' WHERE owner_username IS NULL;
ALTER TABLE account ALTER COLUMN owner_username SET NOT NULL;
CREATE INDEX idx_account_owner ON account (owner_username);

-- A transfer can be reversed at most once; the reversal points back at the original.
ALTER TABLE transfer ADD COLUMN reversal_of UUID REFERENCES transfer (id);
ALTER TABLE transfer ADD CONSTRAINT uk_transfer_reversal_of UNIQUE (reversal_of);
