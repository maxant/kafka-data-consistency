CREATE TABLE T_COMPONENTS (
  ID                      VARCHAR(36),          -- pk
  PARENT_ID               VARCHAR(36),          -- id of parent in tree, only null when this is parent
  CONTRACT_ID             VARCHAR(36) NOT NULL, -- to which contract does this component belong?
  COMPONENTDEFINITION_ID  VARCHAR(99) NOT NULL, -- name of defining class
  PRODUCT_ID              VARCHAR(99),          -- optional name of the product containing this component
  CONFIGURATION           JSON,                 -- variable, depending on defining class
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
