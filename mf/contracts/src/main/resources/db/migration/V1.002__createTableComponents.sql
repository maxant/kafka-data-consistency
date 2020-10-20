CREATE TABLE T_COMPONENTS (
  ID                    VARCHAR(36), -- pk
  PARENT_ID             VARCHAR(36), -- id of parent in tree
  PRODUCTCOMPONENT_ID   VARCHAR(50), -- id of defining class
  CONTRACT_ID           VARCHAR(36), -- to which contract does this component belong?
  CONFIGURATION         JSON,        -- variable, depending on defining class
  PRIMARY KEY(ID)
)
;

-- fk to contracts
ALTER TABLE T_COMPONENTS
ADD CONSTRAINT FK_COMPONENTS_CONTRACT_ID
FOREIGN KEY (CONTRACT_ID) REFERENCES T_CONTRACTS(ID);

-- parent references other existing rows in same table
ALTER TABLE T_COMPONENTS
ADD CONSTRAINT FK_COMPONENTS_PARENT_ID
FOREIGN KEY (PARENT_ID) REFERENCES T_COMPONENTS(ID);
