CREATE TABLE T_CONTRACTS (
  ID            VARCHAR(36),
  PRODUCT_ID    VARCHAR(100), -- eg 'milk choco shake'
  STARTTIME     DATETIME(3),    -- valid from, inclusive
  ENDTIME       DATETIME(3),    -- valid to, inclusive
  PRIMARY KEY(ID)
)
;
