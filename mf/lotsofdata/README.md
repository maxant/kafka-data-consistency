
    # create user in linux too, using same password, so he can access the import file
    forget this: sudo adduser mfcontracts
    ditto: sudo chown mfcontracts:mfcontracts import-contracts.csv

    mysql -u root -p mfcontracts

    -- not used: grant file on *.* to mfcontracts@localhost identified by 'secret';


LOAD DATA LOCAL INFILE 'import-contracts.csv' INTO TABLE mfcontracts.T_CONTRACTS
FIELDS TERMINATED by ','
ENCLOSED BY '\"'
LINES TERMINATED by '\n'
IGNORE 1 ROWS
(ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
;


select * from mfcontracts.T_CONTRACTS;
delete from mfcontracts.T_CONTRACTS;
