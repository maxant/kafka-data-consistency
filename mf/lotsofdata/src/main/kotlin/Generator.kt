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

        private var shuttingDown = false

        @JvmStatic
        fun main(args: Array<String>) {
            val r = Random()
            Class.forName("com.mysql.cj.jdbc.Driver")

            DriverManager.getConnection("jdbc:mysql://retropie:3306/mfcontracts?allowLoadLocalInfile=true","mfcontracts", "secret").use { c ->

                Runtime.getRuntime().addShutdownHook(Thread {
                    teardown(c)
                })

                var start = System.currentTimeMillis()
                c.autoCommit = false
                c.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
                c.prepareStatement("SET FOREIGN_KEY_CHECKS = 0").use { it.executeUpdate() }
                c.prepareStatement("SET UNIQUE_CHECKS = 0").use { it.executeUpdate() }
                c.prepareStatement("SET sql_log_bin = 0").use { it.executeUpdate() }
                c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=268435456").use { it.executeUpdate() }
                c.prepareStatement("ALTER TABLE T_CONTRACTS2 DISABLE KEYS").use { it.executeUpdate() }
                c.prepareStatement("ALTER TABLE T_COMPONENTS2 DISABLE KEYS").use { it.executeUpdate() }
                var time = System.currentTimeMillis() - start
                println("setup done in $time ms")

                start = System.currentTimeMillis()
                c.prepareStatement("LOCK TABLES T_CONTRACTS2 WRITE, T_COMPONENTS2 WRITE").use { it.executeUpdate() }
                time = System.currentTimeMillis() - start
                println("locked in $time ms")

                var numComponents = 0
                var n = 0
                while(numComponents < 20_000_000){
                    n++

                    for(j in 1..20) {
                        println("ITERATION $n/$j")

                        val filename = "/w/kafka-data-consistency/mf/lotsofdata/import-contracts2.csv"
                        val filename2 = "/w/kafka-data-consistency/mf/lotsofdata/import-components2.csv"
                        val file = File(filename)
                        val file2 = File(filename2)
                        if(file.exists()) file.delete()
                        if(file2.exists()) file2.delete()
                        val out = BufferedWriter(FileWriter(file), 256*1024)
                        val out2 = BufferedWriter(FileWriter(file2), 256*1024)

                        start = System.currentTimeMillis()
                        var bytes = 0L
                        for(i in 1..1_000) {
                            val contractId = UUID.randomUUID()
                            val startTime = LocalDateTime.now().withHour(12).plusDays(r.nextInt(365).toLong()).withNano(0)
                            val endTime = startTime.plusYears(r.nextInt(6).toLong())
                            val s = startTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                            val e = endTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                            val at = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                            val sync = System.currentTimeMillis()
                            // ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY
                            val text = """"$contractId", "$s.000","$e.000","RUNNING","$sync","$at.681","john.smith","$at.000","john.smith","$at.000","john.smith ","$at.000","jane.smith""""
                            out.write(text)
                            out.newLine()
                            bytes += text.length

                            var parentId: UUID? = null
                            for(comp in 1..30) {
                                val componentId = UUID.randomUUID()
                                // ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION
                                val parent = if(parentId == null) { "NULL" } else { "'${parentId}'" }
                                val text2 = """'$componentId',$parent,'$contractId','CardboardBox',NULL,'[{"c**":"IntConfiguration","name":"SPACES","clazz":"int","units":"NONE","value":"10"},{"c**":"IntConfiguration","name":"QUANTITY","clazz":"int","units":"PIECES","value":"${r.nextInt(100)}"},{"c**":"MaterialConfiguration","name":"MATERIAL","clazz":"Material","units":"NONE","value":"CARDBOARD"}]'"""
                                out2.write(text2)
                                out2.newLine()
                                bytes += text2.length
                                parentId = componentId
                                numComponents++
                            }
                        }
                        out.flush()
                        out.close()
                        out2.flush()
                        out2.close()
                        time = System.currentTimeMillis() - start
                        println("Wrote ${bytes/1024}KB in $time ms => ${DecimalFormat("#.00").format(bytes / 1024.0 / 1024L / (time / 1000.0))} MB/s.")

                        start = System.currentTimeMillis()
                        c.prepareStatement("""
                    LOAD DATA LOCAL INFILE '$filename' INTO TABLE mfcontracts.T_CONTRACTS2
                    FIELDS TERMINATED by ','
                    ENCLOSED BY '\"'
                    LINES TERMINATED by '\n'
                    (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY)
                """.trimIndent()).use { println("load contracts: ${it.executeUpdate()}") }
                        time = System.currentTimeMillis() - start
                        println("loaded contracts in $time ms")

                        start = System.currentTimeMillis()
                        c.prepareStatement("""
                    LOAD DATA LOCAL INFILE '$filename2' INTO TABLE mfcontracts.T_COMPONENTS2
                    FIELDS TERMINATED by ','
                    ENCLOSED BY '\''
                    LINES TERMINATED by '\n'
                    (ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION)
                """.trimIndent()).use { println("load components: ${it.executeUpdate()}") }
                        time = System.currentTimeMillis() - start
                        println("loaded components in $time ms")

                        start = System.currentTimeMillis()
                        c.commit()
                        time = System.currentTimeMillis() - start
                        println("committed in $time ms")
                    }
                }

                teardown(c)
            }
        }

        private fun teardown(c: Connection) {
            if(!shuttingDown) {
                shuttingDown = true
                println("")
                println(">>>>>> SHUTTING DOWN")
                println("")

                var start = System.currentTimeMillis()
                c.prepareStatement("UNLOCK TABLES").use { it.executeUpdate() }
                var time = System.currentTimeMillis() - start
                println("unlocked in $time ms")

                start = System.currentTimeMillis()
                c.prepareStatement("SET UNIQUE_CHECKS = 1").use { it.executeUpdate() }
                time = System.currentTimeMillis() - start
                println("enabled unique checks $time ms")

                start = System.currentTimeMillis()
                c.prepareStatement("SET FOREIGN_KEY_CHECKS = 1").use { it.executeUpdate() }
                time = System.currentTimeMillis() - start
                println("enabled FK checks $time ms")

                start = System.currentTimeMillis()
                c.prepareStatement("ALTER TABLE T_COMPONENTS2 ENABLE KEYS").use { it.executeUpdate() }
                time = System.currentTimeMillis() - start
                println("enabled component keys $time ms")

                start = System.currentTimeMillis()
                c.prepareStatement("ALTER TABLE T_CONTRACTS2 ENABLE KEYS").use { it.executeUpdate() }
                time = System.currentTimeMillis() - start
                println("enabled contract keys $time ms")

                c.prepareStatement("SET GLOBAL innodb_buffer_pool_size=134217728").use { it.executeUpdate() }
                println("reset innodb buffer pool size")
            } else {
                print("already in the process of shutting down")
            }
        }
    }
}