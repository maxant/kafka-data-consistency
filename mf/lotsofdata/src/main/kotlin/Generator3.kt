import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class Generator3 {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val r = Random()
            var start = 0L
            var time = 0L

            // https://stackoverflow.com/questions/28804626/use-cql-copy-for-map-data

            var numComponents = 0
            var n = 0
            while (numComponents < 20_000_000) {
                n++

                for (j in 1..20) {
                    println("ITERATION $n/$j")

                    val filename = "/w/kafka-data-consistency/mf/lotsofdata/import-contracts3.csv"
                    val filename2 = "/w/kafka-data-consistency/mf/lotsofdata/import-components3.csv"
                    val file = File(filename)
                    val file2 = File(filename2)
                    if (file.exists()) file.delete()
                    if (file2.exists()) file2.delete()
                    val out = BufferedWriter(FileWriter(file), 256 * 1024)
                    val out2 = BufferedWriter(FileWriter(file2), 256 * 1024)

                    start = System.currentTimeMillis()
                    var bytes = 0L
                    for (i in 1..1_000) {
                        val contractId = UUID.randomUUID()
                        val startTime =
                            LocalDateTime.now().withHour(12).plusDays(r.nextInt(365).toLong()).withNano(0)
                        val endTime = startTime.plusYears(r.nextInt(6).toLong())
                        val s = startTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                        val e = endTime.format(DateTimeFormatter.ISO_DATE_TIME).replace('T', ' ')
                        val at = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_DATE_TIME)
                            .replace('T', ' ')
                        val sync = System.currentTimeMillis()
                        // ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY
                        val text =
                            """$contractId|$s|$e|RUNNING|$sync|$at|john.smith|$at|john.smith|$at|john.smith|$at|jane.smith"""
                        out.write(text)
                        out.newLine()
                        bytes += text.length

                        var parentId: UUID? = null
                        for (comp in 1..30) {
                            val componentId = UUID.randomUUID()
                            // ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION
                            val parent = if (parentId == null) {
                                "NULL"
                            } else {
                                "$parentId"
                            }
                            val text2 =
                                """$componentId,$parent,$contractId,CardboardBox,NULL,[{"c**":"IntConfiguration","name":"SPACES","clazz":"int","units":"NONE","value":"10"},{"c**":"IntConfiguration","name":"QUANTITY","clazz":"int","units":"PIECES","value":"${
                                    r.nextInt(100)
                                }"},{"c**":"MaterialConfiguration","name":"MATERIAL","clazz":"Material","units":"NONE","value":"CARDBOARD"}]"""
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
                    println("Wrote ${bytes / 1024}KB in $time ms => ${DecimalFormat("#.00").format(bytes / 1024.0 / 1024L / (time / 1000.0))} MB/s.")
/*
                    start = System.currentTimeMillis()
                    c.prepareStatement(
                        """
                copy t_contracts2(ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP, CREATED_AT, CREATED_BY, OFFERED_AT, OFFERED_BY, ACCEPTED_AT, ACCEPTED_BY, APPROVED_AT, APPROVED_BY) 
                from '/w/kafka-data-consistency/mf/lotsofdata/import-contracts3.csv' 
                with DELIMITER ',' CSV QUOTE '"';
            """.trimIndent()
                    ).use { println("load contracts: ${it.executeUpdate()}") }
                    time = System.currentTimeMillis() - start
                    println("loaded contracts in $time ms")

                    start = System.currentTimeMillis()
                    c.prepareStatement(
                        """
                copy t_components2(ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION) 
                from '/w/kafka-data-consistency/mf/lotsofdata/import-components3.csv' 
                with DELIMITER ',' CSV QUOTE '''';
            """.trimIndent()
                    ).use { println("load components: ${it.executeUpdate()}") }
                    time = System.currentTimeMillis() - start
                    println("loaded components in $time ms")

                    start = System.currentTimeMillis()
                    c.commit()
                    time = System.currentTimeMillis() - start
                    println("committed in $time ms")
*/
                }
            }
        }
    }
}