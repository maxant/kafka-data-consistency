package ch.maxant.kdc.mf.waitingroom.boundary

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.lang.IllegalArgumentException
import java.lang.String.format
import java.math.BigInteger
import java.time.Duration
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/waitingroom")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class WaitingroomResource(
        @ConfigProperty(name = "kafka.bootstrap.servers")
        val bootstrapServers: String,

        @Inject
        val managedExecutor: ManagedExecutor

) {
    val log: Logger = Logger.getLogger(this.javaClass)
    lateinit var producer: Producer<String, String>
    lateinit var consumer01: Consumer<String, String> // a consumer that waits at least one second
    lateinit var consumer10: Consumer<String, String> // a consumer that waits at least ten seconds

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {
        log.info("starting producers/consumers...")

        var props = Properties()
        props["bootstrap.servers"] = bootstrapServers
        props["acks"] = "all"
        producer = KafkaProducer(props, StringSerializer(), StringSerializer())

        props = Properties()
        props["bootstrap.servers"] = bootstrapServers
        props["group.id"] = "mf-waitingroom"
        props["enable.auto.commit"] = "true"
        props["auto.commit.interval.ms"] = "1000"
        props["auto.offset.reset"] = "earliest" // read from start, so that nothing is lost
        //props["max.poll.interval.ms"] = "2000" // default is 5 mins - so thats ok as it is
        consumer01 = KafkaConsumer(props, StringDeserializer(), StringDeserializer())
        consumer01.subscribe(mutableListOf(WAITINGROOM_01))
        managedExecutor.submit { run(1_000, consumer01) }

        //props["max.poll.interval.ms"] = "20000" // default is 5 mins - so thats ok as it is
        consumer10 = KafkaConsumer(props, StringDeserializer(), StringDeserializer())
        consumer10.subscribe(mutableListOf(WAITINGROOM_10))
        managedExecutor.submit { run(10_000, consumer10) }
    }

    @SuppressWarnings("unused")
    fun shutdown(@Observes e: ShutdownEvent) {
        log.info("stopping producers/consumers...")

        producer.close()
        consumer01.close()
        consumer10.close()
    }

    // ------------------------------------------------------------
    //       /\                 /\                 /\
    //       created            now                delay until
    //
    fun run(delay: Int, consumer: Consumer<String, String>) {
        try {
            log.debug("checking waiting room $delay")
            val records: ConsumerRecords<String?, String?> = consumer.poll(Duration.ofSeconds(10)) // so we dont need to wait forever to restart quarkus
            log.debug("received ${records.count()} records requiring a delay in waiting room $delay")
            for (r in records) {
                val timeToDelayUntil = BigInteger(r.headers().lastHeader(DELAY_UNTIL).value())
                log.debug("delay until $timeToDelayUntil")
                val timeNow = BigInteger.valueOf(System.currentTimeMillis())
                log.debug("timeNow $timeNow")
                val timeToWait = timeToDelayUntil.minus(timeNow).longValueExact()
                log.debug("timeToWait $timeToWait")
                if(timeToWait > 0) {
                    log.info("waiting $timeToWait ms in waiting room $delay")
                    Thread.sleep(timeToWait)
                }
                val retryCount = BigInteger(r.headers().lastHeader(RETRY_COUNT)?.value()?:BigInteger("0").toByteArray())
                val originalTopic = String(r.headers().lastHeader(ORIGINAL_TOPIC).value())
                val pr = ProducerRecord(originalTopic, r.key(), r.value())
                r.headers().forEach { pr.headers().add(it) }
                pr.headers().remove(RETRY_COUNT).add(RETRY_COUNT, retryCount.plus(BigInteger.ONE).toByteArray())
                producer.send(pr)
                log.debug("sent record ${pr.key()} on its way back to topic $originalTopic from waiting room $delay")
            }
            consumer.commitSync()
        } catch (e: Exception) {
            // TODO send to DLT? at least this way its probably not committed and will be reprocessed
            log.error("WR001 unable to poll and process", e)
        } finally {
            managedExecutor.runAsync { run(delay, consumer) } // instead of blocking a thread with a while loop
        }
    }

    @POST
    @Path("/test/{delay}")
    @Operation(summary = "test", description = "sends a message to the given waiting room")
    @APIResponses(APIResponse(responseCode = "201"))
    @Tag(name = "test")
    fun test(@Parameter(name = "delay", description = "one of 1 or 10 seconds to delay by", required = true)
             @PathParam("delay") delay: Int
    ): Response {
        val topic = if (delay == 1) WAITINGROOM_01 else if (delay == 10) WAITINGROOM_10 else throw IllegalArgumentException()
        val pr = ProducerRecord(topic, "a", "{someValue}")
        pr.headers()
          .add(DELAY_UNTIL, BigInteger.valueOf(System.currentTimeMillis() + (1_000 * delay)).toByteArray())
          .add(ORIGINAL_TOPIC, "waiting-room-test".toByteArray())
        log.info("set delay until to ${Date(BigInteger(pr.headers().lastHeader(DELAY_UNTIL).value()).longValueExact())}")
        producer.send(pr)
        log.info("sent test message to waiting room $delay")
        return Response.accepted().build()
    }

    companion object {
        const val RETRY_COUNT = "RETRY_COUNT"
        const val DELAY_UNTIL = "DELAY_UNTIL"
        const val ORIGINAL_TOPIC = "ORIGINAL_TOPIC"
        const val WAITINGROOM_01 = "waitingroom-01"
        const val WAITINGROOM_10 = "waitingroom-10"
    }
}
