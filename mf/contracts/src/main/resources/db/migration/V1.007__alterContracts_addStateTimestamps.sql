ALTER TABLE T_CONTRACTS ADD COLUMN OFFERED_AT DATETIME(3);
ALTER TABLE T_CONTRACTS ADD COLUMN OFFERED_BY VARCHAR(50);

ALTER TABLE T_CONTRACTS ADD COLUMN ACCEPTED_AT DATETIME(3);
ALTER TABLE T_CONTRACTS ADD COLUMN ACCEPTED_BY VARCHAR(50);

ALTER TABLE T_CONTRACTS ADD COLUMN APPROVED_AT DATETIME(3);
ALTER TABLE T_CONTRACTS ADD COLUMN APPROVED_BY VARCHAR(50);
