CREATE TABLE contracts
(
  id VARCHAR(36),
  contractnumber VARCHAR(36),
  version INT,
  a VARCHAR(10),
  PRIMARY KEY (id)
);

ALTER TABLE contracts ADD INDEX (contractnumber);
