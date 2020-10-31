CREATE TABLE T_TEMP (
  ID            VARCHAR(36),
  CONTRACT_ID   VARCHAR(36)   NOT NULL,
  ENDTIME       DATETIME(3)   NOT NULL,    -- valid to, inclusive
  VALUE         DECIMAL(9,2)  NOT NULL,    -- value
  PRIMARY KEY(ID)
)
;
