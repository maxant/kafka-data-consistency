CREATE TABLE T_CONTRACTS2 (
  ID            VARCHAR(36),
  STARTTIME     timestamp(3) NOT NULL,    -- valid from, inclusive
  ENDTIME       timestamp(3) NOT NULL,    -- valid to, inclusive
  STATE         VARCHAR(30) NOT NULL,
  PRIMARY KEY(ID)
) ENGINE = MYISAM
;
