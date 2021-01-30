package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.*
import ch.maxant.kdc.mf.billing.definitions.BillingDefinition
import ch.maxant.kdc.mf.billing.definitions.BillingDefinitions
import ch.maxant.kdc.mf.billing.definitions.Periodicity
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.billing.entity.BillsEntity
import ch.maxant.kdc.mf.library.Context
import org.apache.commons.collections4.ListUtils
import java.sql.Timestamp
import java.time.LocalDate
import java.util.*
import java.util.Comparator.comparing
import java.util.Optional.ofNullable
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
class BillingService {

    @Inject
    lateinit var em: EntityManager

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var streamService: StreamService

    private val selectionSql = """
select 
    c.ID,
    c.STARTTIME as CONTRACT_STARTTIME, 
    b.ENDTIME as BILLING_ENDTIME, 
    p.PRODUCT_ID
from mfcontracts.T_CONTRACTS c 
left join mfbilling.T_BILLS b 
    on c.ID = b.CONTRACT_ID  
join mfcontracts.T_COMPONENTS p 
    on c.ID = p.CONTRACT_ID  
where 
  ( b.ENDTIME < ? 
    OR b.ENDTIME is null)
AND c.STARTTIME <= ?
AND c.STATE = 'RUNNING'
AND p.PRODUCT_ID is not null
AND c.ID = 'ffa9c87d-f39c-4518-baaa-c663166720f2'
"""

    @Transactional
    fun billGroup(group: Group) {
        group.contracts.forEach { contract ->
            contract.periodsToBill.forEach { period ->
                val bill = BillsEntity(UUID.randomUUID(), contract.contractId,
                        contract.billingDefinitionId, period.from.atStartOfDay(), period.to.atStartOfDay(), period.price)
                em.persist(bill)
                period.billId = bill.id
            }
        }
    }

    fun startRecurringBilling(from: LocalDate, maxSizeOfGroup: Int): UUID {
        val jobId = UUID.fromString(context.requestId.requestId) // so that it can be sent back to the client waiting for it
        em.createNativeQuery(selectionSql)
            .setParameter(1, from)
            .setParameter(2, from)
            .resultList
            .asSequence() // improves performance according to kotlin docs
            .map { ToBill(it as Array<Any>) } // extract raw data into data class

            // we need to dedup, because we get a row per bill, not contract! => take the highest, and bill from there
            .groupBy(ToBill::contractId)
            .map { getLatest(it.value) }

            // contract must be pre-priced for next billing period starting from the date calculated above
            .map {
                val basePeriodToPrice = when(it.billingDefinition.basePeriodicity) {
                    Periodicity.DAILY -> Period(it.billFrom, it.billFrom.plusDays(1))
                    Periodicity.MONTHLY -> Period(it.billFrom.withDayOfMonth(1), it.billFrom.plusMonths(1).withDayOfMonth(1).minusDays(1))
                    Periodicity.YEARLY -> Period(it.billFrom.withDayOfYear(1), it.billFrom.plusYears(1).withDayOfYear(1).minusDays(1))
                }
                val periodsToBill = when (it.billingDefinition.chosenPeriodicity) {
                    Periodicity.DAILY -> listOf(BillPeriod(it.billFrom, it.billFrom.plusDays(1)))
                    else -> throw TODO()
                }
                ToBill(it, listOf(basePeriodToPrice), periodsToBill)
            }

            // make sensible groups so that we can do list-based updates and not individually-based, in order to reduce the DB comms
            .groupBy { "${it.productId}::${it.billFrom}" }
            .values
            .forEach {
                ListUtils.partition(it, maxSizeOfGroup) // limit group size
                    .forEach { groupOfData ->
                        val groupId = UUID.randomUUID()
                        val contracts = groupOfData.map { toBill ->
                            Contract(jobId, groupId, toBill.contractId, toBill.billingDefinition.definitionId, toBill.basePeriodsToPrice, toBill.periodsToBill)
                        }
                        val group = Group(jobId, groupId, contracts, BillingProcessStep.READ_PRICE)
                        streamService.sendGroup(group)
                    }
            }

        return jobId
    }

    private fun getLatest(it: List<ToBill>) =
        it.stream()
            .sorted(comparing(ToBill::billFrom).reversed())
            .findFirst()
            .orElseThrow()

    data class ToBill(
        val contractId: UUID,
        val contractStart: LocalDate,
        val billFrom: LocalDate,
        val productId: ProductId,
        val billingDefinition: BillingDefinition,
        var basePeriodsToPrice: List<Period> = emptyList(),
        var periodsToBill: List<BillPeriod> = emptyList()
    ) {
        constructor(row: Array<Any>): this(
            UUID.fromString(row[0] as String),
            (row[1] as Timestamp).toLocalDateTime().toLocalDate(),
            ofNullable(row[2]).map { (it as Timestamp).toLocalDateTime().toLocalDate() }.orElse((row[1] as Timestamp).toLocalDateTime().toLocalDate())
                .plusDays(1),
            ProductId.valueOf(row[3] as String),
            BillingDefinitions.get(ProductId.valueOf(row[3] as String))
        )
        constructor(toBill: ToBill, basePeriodsToPrice: List<Period>, periodsToBill: List<BillPeriod>): this(
            toBill.contractId,
            toBill.contractStart,
            toBill.billFrom,
            toBill.productId,
            toBill.billingDefinition,
            basePeriodsToPrice,
            periodsToBill
        )
    }
}

