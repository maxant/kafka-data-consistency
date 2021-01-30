CREATE TABLE T_BILLS (
  ID            VARCHAR(36),
  CONTRACT_ID   VARCHAR(36)   NOT NULL,
  STARTDATE     DATE          NOT NULL,    -- valid from, inclusive
  ENDDATE       DATE          NOT NULL,    -- valid to, inclusive
  VALUE         DECIMAL(9,2)  NOT NULL,    -- value
  PRIMARY KEY(ID)
)
;
