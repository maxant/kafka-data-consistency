import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.Connection
import java.sql.DriverManager
import java.text.DecimalFormat
import java.util.*


class Generator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Class.forName("com.mysql.cj.jdbc.Driver")
            for(j in 1..30) {
                println("ITERATION $j")

                val filename = "/w/kafka-data-consistency/mf/lotsofdata/import-contracts2.csv"
                val file = File(filename)
                if(file.exists()) file.delete()
                val out = BufferedWriter(FileWriter(file), 256*1024)
                var start: Long = System.currentTimeMillis()
                var bytes = 0L
                for(i in 1..10_000) {
                    val text = """"${UUID.randomUUID()}","2021-12-10 00:00:00.000","2023-11-30 00:00:00.000","RUNNING","1609610495490","2021-01-02 18:01:34.681","john.smith","2021-01-02 18:01:36.889","john.smith","2021-01-02 18:01:37.516","john.smith ","2021-01-02 18:01:38.443","jane.smith""""
                    out.write(text)
                    out.newLine()
                    bytes += text.length
                }
                out.close()
                var time = System.currentTimeMillis() - start
                System.out.println("Wrote ${bytes/1024}KB in $time ms => ${DecimalFormat("#.00").format(bytes / 1024.0 / 1024L / (time / 1000.0))} MB/s.")

                DriverManager.getConnection("jdbc:mysql://retropie:3306/mfcontracts?allowLoadLocalInfile=true","mfcontracts", "secret").use { c ->
                    start = System.currentTimeMillis()
                    c.autoCommit = false
                    c.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
                    c.prepareStatement("SET FOREIGN_KEY_CHECKS = 0").use { it.executeUpdate() }
                    c.prepareStatement("SET UNIQUE_CHECKS = 0").use { it.executeUpdate() }
                    c.prepareStatement("SET sql_log_bin = 0").use { it.executeUpdate() }
                    c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=268435456").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_CONTRACTS2 DISABLE KEYS").use { it.executeUpdate() }
                    time = System.currentTimeMillis() - start
                    System.out.println("setup done in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement("LOCK TABLES T_CONTRACTS2 WRITE").use { it.executeUpdate() }
                    c.prepareStatement("""
                        LOAD DATA LOCAL INFILE '$filename' INTO TABLE mfcontracts.T_CONTRACTS2
                        FIELDS TERMINATED by ','
                        ENCLOSED BY '\"'
                        LINES TERMINATED by '\n'
                        (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
                    """.trimIndent()).use { println("load data: ${it.executeUpdate()}") }
                    c.prepareStatement("UNLOCK TABLES").use { it.executeUpdate() }
                    time = System.currentTimeMillis() - start
                    System.out.println("loaded in $time ms")

                    start = System.currentTimeMillis()
                    c.commit()
                    time = System.currentTimeMillis() - start
                    System.out.println("committed in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement("SET UNIQUE_CHECKS = 1").use { it.executeUpdate() }
                    c.prepareStatement("SET FOREIGN_KEY_CHECKS = 1").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_CONTRACTS2 ENABLE KEYS").use { it.executeUpdate() }
                    c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=134217728").use { it.executeUpdate() }
                    time = System.currentTimeMillis() - start
                    System.out.println("tore down in $time ms")
                }
            }
        }
    }
}