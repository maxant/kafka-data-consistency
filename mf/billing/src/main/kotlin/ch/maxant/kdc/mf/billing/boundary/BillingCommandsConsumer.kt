package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.boundary.BillingStreamApplication.Companion.BILL_GROUP
import ch.maxant.kdc.mf.billing.control.BillingService
import ch.maxant.kdc.mf.billing.control.StreamService
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class BillingCommandsConsumer(
    @Inject
    var om: ObjectMapper,

    @Inject
    var context: Context,

    @Inject
    var streamService: StreamService,

    @Inject
    var billingService: BillingService

) : KafkaHandler {

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
            BILL_GROUP -> bill(record.value())
            else -> TODO("unknown message ${context.event}")
        }
    }

    fun bill(value: String) {
        val group = om.readValue<Group>(value)
        bill(group)
    }

    fun bill(group: Group) {
        log.info("billing group ${group.groupId} from job ${group.jobId}")

        try {
            billingService.billGroup(group)
            streamService.sendGroup(Group(group.jobId, group.groupId, group.contracts, BillingProcessStep.COMMS, failedGroupId = group.failedGroupId))
        } catch (e: Exception) {
            if(group.contracts.size == 1) {
                val msg = "failed to bill contract due to ${e.message}, as part of group ${group.groupId} in job ${group.jobId}, " +
                        "with contractId ${group.contracts[0].contractId}"
                log.error(msg, e)
                streamService.sendGroup(Group(group.jobId, group.groupId, group.contracts, null, BillingProcessStep.BILL, failedReason = msg, failedGroupId = group.failedGroupId))
            } else {
                val msg = "failed to bill group because of ${e.message} => sending individually ${group.groupId}"
                log.info(msg)
                // resend individually
                group.contracts.forEach { contract ->
                    val newGroupId = UUID.randomUUID() // we create a new group - one for each individual contract, containing the periods to price
                    val newGroup = Group(group.jobId, newGroupId, listOf(contract), BillingProcessStep.BILL, failedGroupId = group.groupId)
                    streamService.sendGroup(newGroup)
                }
                // now send a group message so that the app can update its state for the old group.
                streamService.sendGroup(Group(group.jobId, group.groupId, group.contracts, null, BillingProcessStep.BILL, failedReason = msg, failedGroupId = group.failedGroupId))
            }
        }
    }
}
