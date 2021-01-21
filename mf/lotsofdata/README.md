
    # move data folder to usb
    sudo cp -R -p /var/lib/mysql/* /media/usb2/mysql

    # create user in linux too, using same password, so he can access the import file
    forget this: sudo adduser mfcontracts
    ditto: sudo chown mfcontracts:mfcontracts import-contracts.csv

    # login as root, so we can do load data
    su
    mysql mfcontracts

    -- not used: grant file on *.* to mfcontracts@localhost identified by 'secret';

https://dba.stackexchange.com/questions/98384/need-to-make-mysqls-load-data-local-infile-load-large-data-faster
https://derwiki.tumblr.com/post/24490758395/loading-half-a-billion-rows-into-mysql

    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET SESSION tx_isolation='READ-UNCOMMITTED';
    SET sql_log_bin = 0;
    ALTER TABLE T_CONTRACTS DISABLE KEYS;
    LOCK TABLES T_CONTRACTS WRITE;

    SET GLOBAL innodb_buffer_pool_size=268435456; -- 256MB should be similar to input file size
    -- dont work: SET GLOBAL innodb_write_io_threads=16; -- add more concurrency - does that work on usb3.0?
    -- dont work: SET GLOBAL innodb_log_buffer_size=268435456; -- 256MB should be similar to input file size

afterwards: 

    SET UNIQUE_CHECKS = 1;
    SET FOREIGN_KEY_CHECKS = 1;
    SET SESSION tx_isolation='READ-REPEATABLE';
    UNLOCK T_CONTRACTS;
    ALTER TABLE verification ENABLE KEYS;

    SET GLOBAL innodb_buffer_pool_size=134217728; -- 128MB
    -- dont work: SET GLOBAL innodb_write_io_threads=4;
    -- dont work: SET GLOBAL innodb_log_buffer_size=8388608; -- 8MB

    mvn compile exec:java -Dexec.mainClass="Generator"

    delete from mfcontracts.T_CONTRACTS;
    
    select * from mfcontracts.T_CONTRACTS;

-- doesnt seem necessary, as "local" in the next statement seems to do this anyway:
-- START TRANSACTION; -- so that each insert is not it's own tx

    LOAD DATA LOCAL INFILE 'import-contracts2.csv' INTO TABLE mfcontracts.T_CONTRACTS
    FIELDS TERMINATED by ','
    ENCLOSED BY '\"'
    LINES TERMINATED by '\n'
    IGNORE 1 ROWS
    (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
    ;

-- see above:
-- COMMIT;

    SELECT table_rows FROM information_schema.tables WHERE table_name = 'T_CONTRACTS';

1'000'000 contract records => 262 MB
18 mins load time
USB 180GB partition over USB3.0

    sudo du -h --max-depth=1

      1.3M	./mysql
      281M	./mfcontracts
      8.0K	./performance_schema
      466M	. (total, 281M in mfcontracts, the rest here:

      48M Jan 21 08:41 ib_logfile0
      48M Jan 21 08:41 ib_logfile1
      76M Jan 21 08:41 ibdata1
      12M Jan 20 21:40 ibtmp1

mfcontracts folder:

    3.3K Jan 20 21:42 T_COMPONENTS.frm
    144K Jan 20 21:42 T_COMPONENTS.ibd
    2.6K Jan 20 21:42 T_CONDITIONS.frm
    144K Jan 20 21:42 T_CONDITIONS.ibd
    2.4K Jan 20 21:42 T_CONTRACTS.frm
    280M Jan 21 08:41 T_CONTRACTS.ibd

