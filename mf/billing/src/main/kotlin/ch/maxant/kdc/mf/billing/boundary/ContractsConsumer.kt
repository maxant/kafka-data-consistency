package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.boundary.BillingConsumer.Companion.PROCESS_GROUP
import ch.maxant.kdc.mf.billing.definitions.BillingDefinitions
import ch.maxant.kdc.mf.billing.definitions.Periodicity
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class ContractsConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var messageBuilder: MessageBuilder,

        @Inject
        var timeMachine: TimeMachine

) : KafkaHandler {

    @Inject
    @Channel("billing-commands-out")
    lateinit var billingCommands: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "contracts-in"

    override fun getRunInParallel() = true

    /** not using kafka streams api here, because we need easy access to headers */
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        val command = om.readTree(record.value())
        when (context.event) {
            "APPROVED_CONTRACT" -> billApprovedContract(command)
        }
    }

    private fun billApprovedContract(command: JsonNode) {
        log.info("creating bill for new contract")
        val contract = om.readValue<ContractDto>(command.get("contract").toString())
        val productId = ProductId.valueOf(command.get("productId").textValue())

        val defns = BillingDefinitions.get(productId)
        val today = timeMachine.today()

        val basePeriodsToPrice = when (defns.basePeriodicity) {
            Periodicity.MONTHLY -> calculateBasePeriodsForMonthly(today, contract.start.toLocalDate(), contract.end.toLocalDate(), defns.referenceDay)
            else -> throw TODO()
        }
        log.info("calculated base periods $basePeriodsToPrice")

        val periodsToBill = when (defns.chosenPeriodicity) {
            Periodicity.DAILY -> listOf(Period(contract.start.toLocalDate(), contract.start.toLocalDate()))
            else -> throw TODO()
        }
        log.info("calculated periods to bill $periodsToBill")

        // create a new job with one group and one contract in that group
        val jobId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val group = Group(jobId, groupId, listOf(Contract(jobId, groupId, contract.id, basePeriodsToPrice, periodsToBill)))
        sendGroup(group)
    }

    /**
     * too complex to think about right now.
     * just ensure that we have some prices calculated for the first base period, and any time before it.
     * the time before the first month will have the same basis. since we bill in advance, we calculate the
     * price for next month, based on data from this month.
     * there can be more than one, if today isnt the reference day in the period
     * TODO if the contract starts in more than a month, then we dont need to bill the customer now!
     */
    fun calculateBasePeriodsForMonthly(today: LocalDate, start: LocalDate, end: LocalDate, referenceDay: Int): List<Period> {
        val dayOfMonth = start.dayOfMonth
        return when {
            dayOfMonth < referenceDay -> {
                // case (1)
                // 1 2 3 4 5 6 7 8 9 ... 30 1 2 3 4 5
                //         |- referenceDay
                //   |- start
                //   |-P1--|-P2 (whole month)-----|
                listOf(
                        // the period up to the reference day
                        Period(start, start.withDayOfMonth(referenceDay)),

                        // a whole month from the reference day
                        Period(start.withDayOfMonth(referenceDay), start.plusMonths(1).withDayOfMonth(referenceDay))
                )
            }
            dayOfMonth == referenceDay -> {
                // case (2)
                // 1 2 3 4 5 6 7 8 9 ... 30 1 2
                //   |- referenceDay
                //   |- start
                //   |-P1---------------------|
                //
                listOf(
                        // a whole month from the reference day, start
                        Period(start, start.plusMonths(1))
                )
            }
            else -> { // dayOfMonth > referenceDay
                // case (3)
                // 1 2 3 4 5 6 7 8 9 ......... 30 1 2 3 4 5
                //   |- referenceDay
                //         |- start
                //         |-P1-(only 27 days!)-----|-P2 (whole month)-|
                listOf(
                        // the period from start to the end of the month
                        Period(start, start.plusMonths(1).withDayOfMonth(referenceDay)),

                        // a whole month from the next reference day
                        Period(start.plusMonths(1).withDayOfMonth(referenceDay), start.plusMonths(2).withDayOfMonth(referenceDay))
                )
            }
        }
    }

    private fun sendGroup(group: Group) {
        billingCommands.send(messageBuilder.build(null, group, command = PROCESS_GROUP))
        log.info("published bill group command")
    }
}

data class ContractDto(val id: UUID, val start: LocalDateTime, val end: LocalDateTime)

