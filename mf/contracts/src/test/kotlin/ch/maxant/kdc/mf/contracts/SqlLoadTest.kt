package ch.maxant.kdc.mf.contracts

import ch.maxant.kdc.mf.contracts.entity.ContractState
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier
import io.agroal.pool.ConnectionPool
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/** investigate if its quicker to do sequential or parallel inserts */
class SqlLoadTest {

    private val countParallel = AtomicInteger(0)
    private val timeParallel = AtomicLong(0)
    private val countSequential = AtomicInteger(0)
    private val timeSequential = AtomicLong(0)
    private val config: AgroalConnectionPoolConfigurationSupplier

    init {
        Class.forName("com.mysql.cj.jdbc.Driver")
        val configSupplier = Supplier<AgroalConnectionFactoryConfiguration> {
            AgroalConnectionFactoryConfigurationSupplier()
                    .autoCommit(false)
                    //.jdbcUrl("jdbc:mysql://mfcontracts:secret@rp:3306/mfcontracts")
                    .jdbcUrl("jdbc:mysql://root:secret@maxant.ch:30300/mfcontracts")
                    .get()
        }
        config = AgroalConnectionPoolConfigurationSupplier()
                .maxSize(5) // like jboss
                .acquisitionTimeout(Duration.of(10, ChronoUnit.MINUTES))
                .initialSize(3)
                .connectionFactoryConfiguration(configSupplier)
    }

    private fun getCp(): ConnectionPool {
        val cp = ConnectionPool(config.get())
        cp.onMetricsEnabled(true)
        return cp
    }

    @Test
    fun test() {
        println("warming up")
        val cpWarmUp = getCp()
        for(i in 1..5) {
            insertOffer(1, cpWarmUp)
            println("warmed up #$i")
        }
        println(cpWarmUp.metrics)
        println("warmup done")
        println("////////////////////////////////////////////////////////////////////////////")
        println("////////////////////////////////////////////////////////////////////////////")
        println("////////////////////////////////////////////////////////////////////////////")
        println("real test starting now")
        val numServices = 50
        val numTests = 50
        val outerlatch = CountDownLatch(numServices + 1)
        for(ns in 1..numServices) { // ten services which use a parallel strategy
            Thread {
                val cp = getCp() // cp for the service
                for(i in 1..numTests) { // each service is constantly writing data
                    val numContracts = 3
                    val latch = CountDownLatch(numContracts)
                    val start = System.currentTimeMillis()
                    for(i in 1..numContracts) { // write each contract in parallel
                        Thread {
                            insertOffer(1, cp)
                            latch.countDown()
                        }.start()
                    }
                    latch.await()
                    val delta = (System.currentTimeMillis() - start)
                    //println("done parallel in ${delta}ms")
                    println("average parallel time = ${(timeParallel.addAndGet(delta) / countParallel.incrementAndGet())}ms after $i tests")
                }
                outerlatch.countDown()
                println(cp.metrics)
            }.start()
        }
        Thread { // sequential test
            val cp = getCp() // cp for the service
            for(i in 1..numTests) { // each service is constantly writing data
                val start = System.currentTimeMillis()
                insertOffer(3, cp)
                val delta = (System.currentTimeMillis() - start)
                //println("done sequential in ${delta}ms")
                println("average sequential time = ${(timeSequential.addAndGet(delta) / countSequential.incrementAndGet())}ms after $i tests")
            }
            outerlatch.countDown()
            println(cp.metrics)
        }.start()

        outerlatch.await()
        println("total time sequential $timeSequential ms")
        println("total time parallel $timeParallel ms")
    }

    /*
    https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html#sysvar_max_connections => 151 conns default
    mysql db running in docker on a box with 8 cores, in the internet.
    50 tests, in order to get a nice average.
    a short warm up time with 5 test sets
    each test inserts 3 contracts with 50 components each (51 rows in total)
    rows are inserted in batches of 25
    sequential test commits after 153 rows
    parallel test commits 3 times, once after each 51 rows, since three client threads and connections are used to create the data
    one connection pool per service with 3-5 connections (only three are needed).

    results taken after about 45 tests:
    - 50 services in parallel + 1 sequentially (many more active connections than cores):
      - XXXXmysqld 10-100% cpu, average maybe 30%?
      - client no noticable load
      - internet upload perhaps 1'000kb/s. very up and down
      - sequential average: 44.3s
      - parallel average: 15.7s
      - strange: time just increased for both, all the way thru the test
    - 30 services in parallel + 1 sequentially (many more active connections than cores):
      - mysqld 10-100% cpu, average maybe 30%?
      - client no noticable load
      - internet upload perhaps 1'000kb/s. very up and down
      - sequential average: 44.3s
      - parallel average: 15.7s
      - strange: time just increased for both, all the way thru the test
    - 7 services in parallel + 1 sequentially (one active connection per core):
      - mysqld 10-30% cpu, average maybe 20%?
      - client no noticable load
      - internet upload perhaps 500kb/s. very up and down
      - sequential average: 18.6s
      - parallel average: 6.9s
      - strange: time just increased for both, all the way thru the test, altho not as much as in upper test
    - 1 service in parallel + 1 sequentially (many less active connections than cores):
      - mysqld 10-20% cpu, average maybe 10%?
      - client no noticable load
      - internet upload perhaps ??kb/s. very up and down
      - sequential average: 8.3s
      - parallel average: 3.0s

    hmm what about the fact that jboss only has say 10 connections, and if its writing in parallel, it has less
    available for servicing other requests, like incoming reads?

     */

    fun insertOffer(numContracts: Int, cp: ConnectionPool) {
        val sqlContract = "insert into T_CONTRACTS (ID, STARTTIME, ENDTIME, STATE, SYNC_TIMESTAMP) values (?,?,?,?,?)"
        val sqlComponents = "insert into T_COMPONENTS (ID, PARENT_ID, CONTRACT_ID, COMPONENTDEFINITION_ID, PRODUCT_ID, CONFIGURATION) values (?,?,?,?,?,?)"
        cp.connection.use { c ->
            try {
                for(i in 1..numContracts) {
                    val contractId = UUID.randomUUID().toString()
                    c.prepareStatement(sqlContract).use { ps ->
                        ps.setString(1, contractId)
                        ps.setTimestamp(2, Timestamp.from(Instant.now()))
                        ps.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(10)))
                        ps.setString(4, ContractState.DRAFT.toString())
                        ps.setLong(5, System.currentTimeMillis())
                        val i = ps.executeUpdate()
                        //println("inserted contract $contractId: ${i}")
                    }
                    c.prepareStatement(sqlComponents).use { ps ->
                        var parentId: String? = null
                        for (i in 1..50) {
                            val id = UUID.randomUUID().toString()
                            ps.setString(1, id)
                            ps.setString(2, parentId)
                            ps.setString(3, contractId)
                            ps.setString(4, "MILKSHAKE_$i")
                            ps.setString(5, "MILKSHAKE")
                            ps.setString(6, """{"name": "asdf", "value": $i}""")
                            ps.addBatch()
                            parentId = id

                            if(i % 25 == 0){
                                val result: IntArray = ps.executeBatch()
                                //println("The number of rows inserted: " + result.size)
                            }
                        }
                    }
                    c.commit()
                }
            }catch(e: Exception) {
                e.printStackTrace()
                c.rollback()
            }
        }
        //println(cp.metrics)
    }
}