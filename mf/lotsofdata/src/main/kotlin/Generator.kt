import java.nio.file.Files
import java.io.FileWriter

import java.io.BufferedWriter
import java.io.File
import java.util.*


class Generator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
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
        }
    }
}