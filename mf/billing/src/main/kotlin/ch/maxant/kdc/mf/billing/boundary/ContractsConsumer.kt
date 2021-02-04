package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.StreamService
import ch.maxant.kdc.mf.billing.definitions.BillingDefinitions
import ch.maxant.kdc.mf.billing.definitions.Periodicity
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
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
        var billingStreamApplication: BillingStreamApplication,

        @Inject
        var streamService: StreamService

) : KafkaHandler {

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "contracts-event-bus-in"

    override fun getRunInParallel() = false

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
        val jobId = UUID.fromString(context.requestId.requestId) // so that it can be sent back to the client waiting for it
        val groupId = UUID.randomUUID()
        val group = Group(jobId, groupId, listOf(
                    Contract(jobId, groupId, contract.id, defn.definitionId, listOf(basePeriodToPrice), periodsToBill)
                ), BillingProcessStep.READ_PRICE)
        streamService.sendGroup(group)
    }

    fun handlePricedGroup(value: String, recalculated: Boolean) {
        val group = om.readValue<PricingCommandGroupResult>(value)
        if(group.failed) {
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
        val originalContract = getContract(getGroup(group.groupId), group.commands[0].contractId)
        val processStep = if (recalculated) BillingProcessStep.RECALCULATE_PRICE else BillingProcessStep.READ_PRICE
        streamService.sendGroup(Group(originalContract.jobId, group.groupId, listOf(originalContract), null, processStep, failedReason = group.failedReason))
    }

    /** the following can't fail, because the group is sent to pricing after it's written to the stream which is
     * produced from the KTable, meaning that the ktable store has the data, before hand. */
    private fun getGroup(groupId: UUID) = billingStreamApplication.getGroup(groupId)

    private fun handlePricedGroup_failedGroup(
        group: PricingCommandGroupResult,
        recalculated: Boolean
    ) {
        val processStep = if (recalculated) BillingProcessStep.RECALCULATE_PRICE else BillingProcessStep.READ_PRICE
        val originalGroup = getGroup(group.groupId)
        group.commands.map { it.contractId }.distinct().forEach { contractId ->
            val originalContract = getContract(originalGroup, contractId)
            val newGroupId = UUID.randomUUID() // we create a new group - one for each individual contract, containing the periods to price
            val contract = Contract(
                originalContract.jobId,
                group.groupId,
                originalContract.contractId,
                originalContract.billingDefinitionId,
                originalContract.basePeriodsToPrice,
                emptyList()
            )
            val newGroup = Group(originalContract.jobId, newGroupId, listOf(contract), processStep)
            streamService.sendGroup(newGroup)
        }

        // now send a group message so that the app can update its state for the old group.
        streamService.sendGroup(Group(originalGroup.jobId, originalGroup.groupId, originalGroup.contracts, null, processStep, failedReason = group.failedReason))
    }

    private fun getContract(group: Group, contractId: UUID) = group.contracts.find { it.contractId == contractId }!!

    private fun handlePricedGroup_success(group: PricingCommandGroupResult) {
        val originalGroup = getGroup(group.groupId)
        val contracts = group.commands.map { it.contractId }.distinct().map { contractId ->
            val contract = getContract(originalGroup, contractId)
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
                periodToBill.price = priceByComponent.map { it.price.total }.max()!!
            }
            contract
        }
        streamService.sendGroup(Group(contracts[0].jobId/*they all have the same one!*/, group.groupId, contracts, BillingProcessStep.BILL))
    }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// contract event => sent from contracts, e.g. for an approved contract
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class ContractDto(val id: UUID, val start: LocalDateTime, val end: LocalDateTime)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing event => sent from pricing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingCommandGroupResult(val groupId: UUID,
                                     val commands: List<PricingCommandResult>,
                                     val failed: Boolean,
                                     var failedReason: String?)

data class PricingCommandResult(val contractId: UUID,
                                val priceByComponentId: Map<UUID, ComponentPriceWithValidity>)


data class ComponentPriceWithValidity(val price: Price, val from: LocalDate, val to: LocalDate)

data class Price(val total: BigDecimal, val tax: BigDecimal)
