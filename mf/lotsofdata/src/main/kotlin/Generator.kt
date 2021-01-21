import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.Connection
import java.sql.DriverManager
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class Generator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val r = Random()
            Class.forName("com.mysql.cj.jdbc.Driver")
            for(j in 1..30) {
                println("ITERATION $j")

                val filename = "/w/kafka-data-consistency/mf/lotsofdata/import-contracts2.csv"
                val filename2 = "/w/kafka-data-consistency/mf/lotsofdata/import-components2.csv"
                val file = File(filename)
                val file2 = File(filename2)
                if(file.exists()) file.delete()
                if(file2.exists()) file2.delete()
                val out = BufferedWriter(FileWriter(file), 256*1024)
                val out2 = BufferedWriter(FileWriter(file2), 256*1024)
                var start: Long = System.currentTimeMillis()
                var bytes = 0L
                for(i in 1..1_000) {
                    val contractId = UUID.randomUUID()
                    val startTime = LocalDateTime.now().withHour(12).plusDays(r.nextInt(365).toLong())
                    val endTime = startTime.plusYears(r.nextInt(6).toLong())
                    val s = startTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                    val e = endTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                    val at = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                    val sync = System.currentTimeMillis()
                    // ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY
                    val text = """"${contractId}", "$s.000","$e.000","RUNNING","$sync","$at.681","john.smith","$at.000","john.smith","$at.000","john.smith ","$at.000","jane.smith""""
                    out.write(text)
                    out.newLine()
                    bytes += text.length

                    var parentId: UUID? = null
                    for(c in 1..30) {
                        val componentId = UUID.randomUUID()
                        // ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION
                        val text2 = """%$componentId%, ${if(parentId == null){"NULL"} else {"%${parentId}%"}}, %$contractId%, %CardboardBox%, NULL, %[{"c**": "IntConfiguration", "name": "SPACES", "clazz": "int", "units": "NONE", "value": "10"}, {"c**": "IntConfiguration", "name": "QUANTITY", "clazz": "int", "units": "PIECES", "value": "${r.nextInt(100)}"}, {"c**": "MaterialConfiguration", "name": "MATERIAL", "clazz": "Material", "units": "NONE", "value": "CARDBOARD"}]%"""
                        out2.write(text2)
                        out2.newLine()
                        bytes += text2.length
                        parentId = componentId
                    }
                }
                out.close()
                var time = System.currentTimeMillis() - start
                println("Wrote ${bytes/1024}KB in $time ms => ${DecimalFormat("#.00").format(bytes / 1024.0 / 1024L / (time / 1000.0))} MB/s.")

                DriverManager.getConnection("jdbc:mysql://retropie:3306/mfcontracts?allowLoadLocalInfile=true","mfcontracts", "secret").use { c ->
                    start = System.currentTimeMillis()
                    c.autoCommit = false
                    c.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
                    c.prepareStatement("SET FOREIGN_KEY_CHECKS = 0").use { it.executeUpdate() }
                    c.prepareStatement("SET UNIQUE_CHECKS = 0").use { it.executeUpdate() }
                    c.prepareStatement("SET sql_log_bin = 0").use { it.executeUpdate() }
                    //c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=268435456").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_CONTRACTS2 DISABLE KEYS").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_COMPONENTS2 DISABLE KEYS").use { it.executeUpdate() }
                    time = System.currentTimeMillis() - start
                    println("setup done in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement("LOCK TABLES T_CONTRACTS2 WRITE, T_COMPONENTS2 WRITE").use { it.executeUpdate() }
                    c.prepareStatement("""
                        LOAD DATA LOCAL INFILE '$filename' INTO TABLE mfcontracts.T_CONTRACTS2
                        FIELDS TERMINATED by ','
                        ENCLOSED BY '\"'
                        LINES TERMINATED by '\n'
                        (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
                    """.trimIndent()).use { println("load data: ${it.executeUpdate()}") }
                    time = System.currentTimeMillis() - start
                    println("loaded contracts in $time ms")
                    start = System.currentTimeMillis()
                    c.prepareStatement("""
                        LOAD DATA LOCAL INFILE '$filename2' INTO TABLE mfcontracts.T_COMPONENTS2
                        FIELDS TERMINATED by ','
                        ENCLOSED BY '%'
                        LINES TERMINATED by '\n'
                        (ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION)
                    """.trimIndent()).use { println("load data: ${it.executeUpdate()}") }
                    time = System.currentTimeMillis() - start
                    println("loaded components in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement("UNLOCK TABLES").use { it.executeUpdate() }
                    c.commit()
                    time = System.currentTimeMillis() - start
                    println("unlocked and committed in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement("SET UNIQUE_CHECKS = 1").use { it.executeUpdate() }
                    c.prepareStatement("SET FOREIGN_KEY_CHECKS = 1").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_CONTRACTS2 ENABLE KEYS").use { it.executeUpdate() }
                    c.prepareStatement("ALTER TABLE T_COMPONENTS2 ENABLE KEYS").use { it.executeUpdate() }
                    //c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=134217728").use { it.executeUpdate() }
                    time = System.currentTimeMillis() - start
                    println("tore down in $time ms")
                }
            }
        }
    }
}