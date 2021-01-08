package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class BillingConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var messageBuilder: MessageBuilder

) : KafkaHandler {

    @Inject
    @Channel("contracts-event-bus-out")
    lateinit var contractEventBus: Emitter<String>

    @Inject
    @Channel("events-out")
    lateinit var events: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "billing-in"

    override fun getRunInParallel() = true

    /**
     * this is the general entry point into the billing application. this is either called because a contract is
     * approved, or because the scheduled recurring billing process has found contracts that are not yet billed.
     * not using kafka streams api here, because we need easy access to headers.
     */
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.command) {
            PROCESS_GROUP -> sendToPricing(record.value())
            else -> TODO("unknown message ${context.event}")
        }
    }

    fun sendToPricing(value: String) {
        val group = om.readValue<Group>(value)
        log.info("billing group ${group.groupId} from job ${group.jobId}")

        val commands = mutableListOf<PricingCommand>()
        for((i, contract) in group.contracts.withIndex()) {
            for(period in contract.basePeriodsToPrice) {
                commands.add(PricingCommand(contract.contractId, period.from, period.to))
            }
        }
        sendCommand(PricingCommandGroup(group.groupId, commands))

        group.contracts.forEach { sendEvent_SentToPricing(it) }
    }

    private fun sendEvent_SentToPricing(contract: Contract) {
        val event = Event(Action.SENT_TO_PRICING, contract.jobId, contract.groupId, contract.contractId)
        val md = OutgoingKafkaRecordMetadata.builder<String>()
                        .withKey(contract.jobId.toString())
                        .build()
        val msg = Message.of(om.writeValueAsString(event))
        events.send(msg.addMetadata(md))
    }

    private fun sendCommand(pricingCommandGroup: PricingCommandGroup) {
        contractEventBus.send(messageBuilder.build(pricingCommandGroup.groupId, pricingCommandGroup, command = "CALCULATE_PRICES"))
        log.info("published pricing command groupId ${pricingCommandGroup.groupId} with contractIds ${pricingCommandGroup.commands.map {it.contractId}}")
    }

    companion object {
        const val PROCESS_GROUP = "PROCESS_GROUP"
    }
}

data class PricingCommandGroup(val groupId: UUID, val commands: List<PricingCommand>)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

data class Group(val jobId: UUID, val groupId: UUID, val contracts: List<Contract>, val started: LocalDateTime = LocalDateTime.now())

data class Contract(val jobId: UUID, val groupId: UUID, val contractId: UUID, val basePeriodsToPrice: List<Period>, val periodsToBill: List<Period>)

data class Period(val from: LocalDate, val to: LocalDate)
