package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.StreamService
import ch.maxant.kdc.mf.billing.definitions.BillingDefinitions
import ch.maxant.kdc.mf.billing.definitions.Periodicity
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jboss.logging.Logger
import java.math.BigDecimal
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
        var billingStreamApplication: BillingStreamApplication,

        @Inject
        var streamService: StreamService

) : KafkaHandler {

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "contracts-event-bus-in"

    override fun getRunInParallel() = true

    /** not using kafka streams api here, because we need easy access to headers */
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.event) {
            "APPROVED_CONTRACT" -> startBillingOfApprovedContract(om.readTree(record.value()))
            "READ_PRICES_FOR_GROUP_OF_CONTRACTS" -> handlePricedGroup(record.value(), false)
            "RECALCULATED_PRICES_FOR_GROUP_OF_CONTRACTS" -> handlePricedGroup(record.value(), true)
        }
    }

    private fun startBillingOfApprovedContract(command: JsonNode) {
        val contract = om.readValue<ContractDto>(command.get("contract").toString())
        log.info("creating bill for new contract ${contract.id} by reading the price")
        val productId = ProductId.valueOf(command.get("productId").textValue())

        val defn = BillingDefinitions.get(productId)

        // contract is prepriced for duration => just go get the price at the start and use it for the first billing period
        val basePeriodToPrice = Period(contract.start.toLocalDate(), contract.start.plusDays(1).toLocalDate())

        val periodsToBill = when (defn.chosenPeriodicity) {
            Periodicity.DAILY -> listOf(BillPeriod(contract.start.toLocalDate(), contract.start.toLocalDate()))
            else -> throw TODO()
        }
        log.info("calculated periods to bill $periodsToBill")

        // create a new job with one group and one contract in that group
        val jobId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val group = Group(jobId, groupId, listOf(
                    Contract(jobId, groupId, contract.id, defn.definitionId, listOf(basePeriodToPrice), periodsToBill)
                ), BillingProcessStep.READ_PRICE)
        streamService.sendGroup(group)
    }

    private fun recurringBillExistingContract(command: JsonNode) {
        log.info("recurring bill for existing contract")
        TODO()
        /*
        val group = om.readValue<List<ContractDto>>(command.get("contracts").toString())
        val productId = ProductId.valueOf(command.get("productId").textValue())

        val defn = BillingDefinitions.get(productId)
        val today = timeMachine.today()
for a new contract, we just need to fetch a price.
during recurring billing, we need to calculate a new price.
so the command needs a descriminator. or perhaps its a difference command!;
        val basePeriodsToPrice = when (defn.basePeriodicity) {
            Periodicity.MONTHLY -> calculateBasePeriodsForMonthly(today, contract.start.toLocalDate(), contract.end.toLocalDate(), defn.referenceDay)
            else -> throw TODO()
        }
        log.info("calculated base periods $basePeriodsToPrice")

        val periodsToBill = when (defn.chosenPeriodicity) {
            Periodicity.DAILY -> listOf(Period(contract.start.toLocalDate(), contract.start.toLocalDate()))
            else -> throw TODO()
        }
        log.info("calculated periods to bill $periodsToBill")

        // create a new job with one group and one contract in that group
        val jobId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val group = Group(jobId, groupId, listOf(Contract(jobId, groupId, contract.id, defn.definitionId, basePeriodsToPrice, periodsToBill)))
        sendPriceGroup(group)
         */
    }

    /**
     * too complex to think about right now.
     * just ensure that we have some prices calculated for the first base period, and any time before it.
     * the time before the first month will have the same basis. since we bill in advance, we calculate the
     * price for next month, based on data from this month.
     * there can be more than one, if today isnt the reference day in the period
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

    fun handlePricedGroup(value: String, recalculated: Boolean) {
        val group = om.readValue<PricingCommandGroupResult>(value)
        if(group.commands.any { it.failed }) {
            if(group.commands.size == 1) {
                handlePricedGroup_failedSingleContract(group, recalculated)
            } else {
                handlePricedGroup_failedGroup(group, recalculated)
            }
        } else {
            handlePricedGroup_success(group)
        }
    }

    private fun handlePricedGroup_failedSingleContract(
        group: PricingCommandGroupResult,
        recalculated: Boolean
    ) {
        val originalContract = getContract(group.groupId, group.commands[0].contractId)
        val processStep = if (recalculated) BillingProcessStep.RECALCULATE_PRICE else BillingProcessStep.READ_PRICE
        streamService.sendGroup(Group(group.jobId, group.groupId, listOf(originalContract), processStep, processStep))
    }

    /** the following can't fail, because the group is sent to pricing after it's written to the stream which is
     * produced from the KTable, meaning that the ktable store has the data, before hand. */
    private fun getContract(groupId: UUID, contractId: UUID) = billingStreamApplication.getContract(groupId, contractId)

    private fun handlePricedGroup_failedGroup(
        group: PricingCommandGroupResult,
        recalculated: Boolean
    ) {
        group.commands.map { it.contractId }.distinct().forEach { contractId ->
            val originalContract = getContract(group.groupId, group.commands[0].contractId)
            val newGroupId =
                UUID.randomUUID() // we create a new group - one for each individual contract, containing the periods to price
            val contract = Contract(
                group.jobId,
                group.groupId,
                originalContract.contractId,
                originalContract.billingDefinitionId,
                originalContract.basePeriodsToPrice,
                emptyList()
            )
            val processStep = if (recalculated) BillingProcessStep.RECALCULATE_PRICE else BillingProcessStep.READ_PRICE
            val newGroup = Group(group.jobId, newGroupId, listOf(contract), processStep)
            streamService.sendGroup(newGroup)
        }
    }

    private fun handlePricedGroup_success(group: PricingCommandGroupResult) {
        val contracts = group.commands.map { it.contractId }.distinct().map { contractId ->
            val contract = getContract(group.jobId, contractId)
            contract.periodsToBill.forEach { periodToBill ->
                val priceByComponent = group.commands
                    .filter { it.contractId == contract.contractId }
                    .flatMap { it.priceByComponentId.values }

                // TODO we dont have the component structure to hand. lets just take the highest price
                // and assume its the root component!
                // of course that doesnt work for cases where we have more than one period per component!!
                // root contains the price to bill
                // the price will always have a bigger range than the bill. See note in definitions!
                // TODO ok, not true, if the customer choses a periodicity smaller than the base periodicity.
                //  that isnt allowed for the moment tho!

                var highestPrice: ComponentPriceWithValidity? = null
                priceByComponent.forEach {
                    if (highestPrice == null || it.price.total > highestPrice!!.price.total) {
                        highestPrice = it
                    }
                }
                periodToBill.price = highestPrice!!.price.total
            }
            contract
        }
        streamService.sendGroup(Group(group.jobId, group.groupId, contracts, BillingProcessStep.BILL))
    }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// contract event => sent from contracts, e.g. for an approved contract
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class ContractDto(val id: UUID, val start: LocalDateTime, val end: LocalDateTime)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing event => sent from pricing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingCommandGroupResult(val jobId: UUID, val groupId: UUID, val commands: List<PricingCommandResult>)

data class PricingCommandResult(val contractId: UUID, val priceByComponentId: Map<UUID, ComponentPriceWithValidity>, val failed: Boolean)

data class ComponentPriceWithValidity(val price: Price, val from: LocalDate, val to: LocalDate)

data class Price(val total: BigDecimal, val tax: BigDecimal)
