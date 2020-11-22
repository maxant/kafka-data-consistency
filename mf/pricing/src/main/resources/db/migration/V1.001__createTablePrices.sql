CREATE TABLE T_PRICES (
  ID            VARCHAR(36),
  CONTRACT_ID   VARCHAR(36)   NOT NULL,
  STARTTIME     DATETIME(3)   NOT NULL,    -- valid from, inclusive
  ENDTIME       DATETIME(3)   NOT NULL,    -- valid to, inclusive
  COMPONENT_ID  VARCHAR(36)   NOT NULL,    -- this is the price of which component?
  PRICING_ID    VARCHAR(36)   NOT NULL,    -- which rule was used to calculate this price?
  PRICE         DECIMAL(9,2)  NOT NULL,    -- total price
  TAX           DECIMAL(9,2)  NOT NULL,    -- of which this much is tax
  PRIMARY KEY(ID)
)
;
