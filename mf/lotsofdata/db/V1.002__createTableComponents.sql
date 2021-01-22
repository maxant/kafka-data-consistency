CREATE TABLE T_COMPONENTS2 (
  ID                      VARCHAR(36),          -- pk
  PARENT_ID               VARCHAR(36),          -- id of parent in tree, only null when this is parent
  CONTRACT_ID             VARCHAR(36) NOT NULL, -- to which contract does this component belong?
  COMPONENTDEFINITION_ID  VARCHAR(99) NOT NULL, -- name of defining class
  PRODUCT_ID              VARCHAR(99),          -- optional name of the product containing this component
  CONFIGURATION           jsonb,                 -- variable, depending on defining class
  PRIMARY KEY(ID)
) ENGINE = MYISAM
;

-- fk to contracts
ALTER TABLE T_COMPONENTS2
ADD CONSTRAINT FK_COMPONENTS2_CONTRACT_ID
FOREIGN KEY (CONTRACT_ID) REFERENCES T_CONTRACTS2(ID);

-- parent references other existing rows in same table
ALTER TABLE T_COMPONENTS2
ADD CONSTRAINT FK_COMPONENTS2_PARENT_ID
FOREIGN KEY (PARENT_ID) REFERENCES T_COMPONENTS2(ID);
