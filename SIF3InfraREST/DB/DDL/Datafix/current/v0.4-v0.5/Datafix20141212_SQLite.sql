ALTER TABLE SIF3_SESSION ADD SECURITY_TOKEN VARCHAR(1000) NULL;
ALTER TABLE SIF3_SESSION ADD SECURITY_TOKEN_EXPIRY DATETIME NULL;

CREATE INDEX SEC_TOKEN_IDX ON SIF3_SESSION (SECURITY_TOKEN ASC, ADAPTER_TYPE ASC);
