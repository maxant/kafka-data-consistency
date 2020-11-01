CREATE TABLE T_CASES (
  ID            VARCHAR(36),
  REFERENCE_ID  VARCHAR(36)   NOT NULL,    -- id used in master component
  TYPE          VARCHAR(36)   NOT NULL,    -- the type of tihs case
  PRIMARY KEY(ID)
)
;

-- refrerenceId MUST have a 1:1 relationship to a case
CREATE UNIQUE INDEX I_CASES_REFERENCE_ID ON T_CASES (REFERENCE_ID);

CREATE TABLE T_TASKS (
  ID          VARCHAR(36),
  CASE_ID     VARCHAR(36)     NOT NULL,    -- id of parent case
  USER_ID     VARCHAR(36)     NOT NULL,    -- id of user who this task is assigned to
  TITLE       VARCHAR(200)    NOT NULL,    -- title of the case
  DESCRIPTION VARCHAR(4000)   NOT NULL,    -- what the user needs to do
  STATE       VARCHAR(40)     NOT NULL,    -- open, done, etc
  PRIMARY KEY(ID)
)
;

-- fk to cases
ALTER TABLE T_TASKS
ADD CONSTRAINT FK_TASKS_CASE_ID
FOREIGN KEY (CASE_ID) REFERENCES T_CASES(ID);

CREATE INDEX I_TASKS_CASE_ID ON T_TASKS (CASE_ID);
