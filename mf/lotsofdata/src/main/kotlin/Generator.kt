import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.DriverManager
import java.util.*


class Generator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            for(j in 1..20) {
                println("ITERATION $j")
                val file = File("./mf/lotsofdata/import-contracts2.csv")
                if(file.exists()) file.delete()
                val out = BufferedWriter(FileWriter(file), 32768)
                var start: Long = System.currentTimeMillis()
                var bytes = 0L
                for(i in 1..1000) {
                    val text = """"${UUID.randomUUID()}","2021-12-10 00:00:00.000","2023-11-30 00:00:00.000","RUNNING","1609610495490","2021-01-02 18:01:34.681","john.smith","2021-01-02 18:01:36.889","john.smith","2021-01-02 18:01:37.516","john.smith ","2021-01-02 18:01:38.443","jane.smith""""
                    out.write(text)
                    out.newLine()
                    bytes += text.length
                }
                out.close()
                val time = System.currentTimeMillis() - start
                System.out.println("Wrote ${bytes/1024}KB in $time ms")
                System.out.println("${(bytes / 1024.0 / 1024L / (time / 1000.0))} MB/s.")

                Class.forName("com.mysql.cj.jdbc.Driver")
                DriverManager.getConnection("jdbc:mysql://localhost:3306/mfcontracts","root", System.getProperty("password")).use { c ->
                    c.prepareStatement("""
    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET SESSION tx_isolation='READ-UNCOMMITTED';
    SET sql_log_bin = 0;
    ALTER TABLE T_CONTRACTS DISABLE KEYS;
    LOCK TABLES T_CONTRACTS WRITE;
    SET GLOBAL innodb_buffer_pool_size=268435456; -- 256MB should be similar to input file size

    LOAD DATA LOCAL INFILE 'import-contracts2.csv' INTO TABLE mfcontracts.T_CONTRACTS2
    FIELDS TERMINATED by ','
    ENCLOSED BY '\"'
    LINES TERMINATED by '\n'
    IGNORE 1 ROWS
    (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
    ;
                    """.trimIndent()).use { ps ->
                        println("updated ${ps.executeUpdate()} rows")
                    }
                    c.prepareStatement("""
    SET UNIQUE_CHECKS = 1;
    SET FOREIGN_KEY_CHECKS = 1;
    SET SESSION tx_isolation='READ-REPEATABLE';
    UNLOCK T_CONTRACTS;
    ALTER TABLE verification ENABLE KEYS;

    SET GLOBAL innodb_buffer_pool_size=134217728; -- 128MB
                    """.trimIndent()).use { ps ->
                        println("updated ${ps.executeUpdate()} rows")
                    }
                }
            }
        }
    }
}