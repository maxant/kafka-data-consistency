CREATE TABLE T_CONTRACTS (
  ID            VARCHAR(36),
  STARTTIME     DATETIME(3) NOT NULL,    -- valid from, inclusive
  ENDTIME       DATETIME(3) NOT NULL,    -- valid to, inclusive
  STATUS        VARCHAR(30) NOT NULL,
  PRIMARY KEY(ID)
)
;
