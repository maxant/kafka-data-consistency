CREATE TABLE T_BILLED_TO (
  CONTRACT_ID   VARCHAR(36)   NOT NULL,
  BILLED_TO     DATE          NOT NULL, -- date of highest bill enddate
  PRIMARY KEY(CONTRACT_ID)
)
;
