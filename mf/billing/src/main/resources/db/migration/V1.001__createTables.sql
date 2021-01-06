CREATE TABLE T_BILLS (
  ID            VARCHAR(36),
  CONTRACT_ID   VARCHAR(36)   NOT NULL,
  STARTTIME     DATETIME(3)   NOT NULL,    -- valid from, inclusive
  ENDTIME       DATETIME(3)   NOT NULL,    -- valid to, inclusive
  VALUE         DECIMAL(9,2)  NOT NULL,    -- value
  PRIMARY KEY(ID)
)
;
