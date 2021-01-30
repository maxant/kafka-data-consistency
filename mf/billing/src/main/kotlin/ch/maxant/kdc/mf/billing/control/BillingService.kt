package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.*
import ch.maxant.kdc.mf.billing.definitions.BillingDefinition
import ch.maxant.kdc.mf.billing.definitions.BillingDefinitions
import ch.maxant.kdc.mf.billing.definitions.Periodicity
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.billing.entity.BilledToEntity
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
    b.BILLED_TO, 
    p.PRODUCT_ID, 
    c.ENDTIME as CONTRACT_ENDTIME 
from mfcontracts.T_CONTRACTS c 
left join mfbilling.T_BILLED_TO b 
    on c.ID = b.CONTRACT_ID 
join mfcontracts.T_COMPONENTS p 
    on c.ID = p.CONTRACT_ID 
where 
  ( b.BILLED_TO < ? 
    OR b.BILLED_TO is null) 
AND c.STARTTIME <= ? 
AND c.ENDTIME >= ? 
AND c.STATE = 'RUNNING' 
AND p.PRODUCT_ID is not null 
AND c.ID = 'ffa9c87d-f39c-4518-baaa-c663166720f2' 
"""

    @Transactional
    fun billGroup(group: Group) {
        group.contracts.forEach { contract ->
            contract.periodsToBill.forEach { period ->
                val bill = BillsEntity(UUID.randomUUID(), contract.contractId,
                        contract.billingDefinitionId, period.from, period.to, period.price)
                em.persist(bill)
                period.billId = bill.id
            }
            val billedToDate = contract.periodsToBill.map { it.to }.max()!!
            val billedTo = em.find(BilledToEntity::class.java, contract.contractId)
            if(billedTo == null) {
                em.persist(BilledToEntity(contract.contractId, billedToDate))
            } else if(billedToDate > billedTo.billedTo) {
                billedTo.billedTo = billedToDate
            }
        }
    }

    fun startRecurringBilling(from: LocalDate, maxSizeOfGroup: Int): RecurringBillingJob {
        val jobId = UUID.fromString(context.requestId.requestId) // so that it can be sent back to the client waiting for it
        var numSelectedContracts = 0
        var numGroups = 0
        em.createNativeQuery(selectionSql)
            .setParameter(1, from)
            .setParameter(2, from)
            .setParameter(3, from)
            .resultList
            .asSequence() // improves performance according to kotlin docs
            .map { ToBill(it as Array<Any>) } // extract raw data into data class

            // we need to dedup, because we get a row per bill, not contract! => take the highest, and bill from there
            .groupBy(ToBill::contractId)
            .map(Map.Entry<UUID, List<ToBill>>::value)
            .map(this::getLatest)
            .map { calculateBillingRequirements(it, from) }

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
                        numSelectedContracts += group.contracts.size
                        numGroups ++
                    }
            }

        return RecurringBillingJob(jobId, numSelectedContracts, numGroups)
    }

    private fun calculateBillingRequirements(it: ToBill, from: LocalDate): ToBill {
        var basePeriodToPrice = when (it.billingDefinition.basePeriodicity) {
            Periodicity.DAILY -> Period(it.billFrom, it.billFrom)
            Periodicity.MONTHLY -> Period(
                it.billFrom.withDayOfMonth(1),
                it.billFrom.plusMonths(1).withDayOfMonth(1).minusDays(1)
            )
            Periodicity.YEARLY -> Period(
                it.billFrom.withDayOfYear(1),
                it.billFrom.plusYears(1).withDayOfYear(1).minusDays(1)
            )
        }
        if (basePeriodToPrice.from < it.contractStart) basePeriodToPrice = Period(it.contractStart, basePeriodToPrice.to)
        if (basePeriodToPrice.to > it.contractEnd) basePeriodToPrice = Period(it.billFrom, it.contractEnd)

        val periodsToBill = when (it.billingDefinition.chosenPeriodicity) {
            Periodicity.DAILY -> calculateDailyPeriodsToBill(it, from)
            else -> throw TODO()
        }

        return ToBill(it, listOf(basePeriodToPrice), periodsToBill)
    }

    private fun calculateDailyPeriodsToBill(it: ToBill, from: LocalDate): List<BillPeriod> {
        // it.billFrom is either:
        //     the day after the last billing
        // or
        //     contract start date

        require(it.billFrom >= it.contractStart) { "problem with $it because selection should ensure billFrom is never less than contract start" }
        require(it.billFrom < it.contractEnd) { "problem with $it because selection should ensure billFrom is never more than contract end" }
        require(it.billFrom <= from) { "problem with $it because we've calculated that we should bill from a date after the required bill date $from" }

        // now create a billing requirement for each day from the last billing (or contract start) until
        // the requested billing date. as we're doing daily, we only bill up to and including that date.
        var currentDate = it.billFrom
        val periods = mutableListOf<BillPeriod>()
        while (currentDate <= from) {
            val period = BillPeriod(currentDate, currentDate)
            periods.add(period)
            currentDate = period.to.plusDays(it.billingDefinition.chosenPeriodicity.numDaysInPeriod.toLong())
        }
        return periods
    }

    private fun getLatest(it: List<ToBill>) =
        it.stream()
            .sorted(comparing(ToBill::billFrom).reversed())
            .findFirst()
            .orElseThrow()

    data class ToBill(
        val contractId: UUID,
        val contractStart: LocalDate,
        val contractEnd: LocalDate,
        val billFrom: LocalDate,
        val productId: ProductId,
        val billingDefinition: BillingDefinition,
        var basePeriodsToPrice: List<Period> = emptyList(),
        var periodsToBill: List<BillPeriod> = emptyList()
    ) {
        constructor(row: Array<Any>): this(
            // 0 c.ID, 1 CONTRACT_STARTTIME, 2 BILLED_TO, 3 PRODUCT_ID, 4 CONTRACT_ENDTIME
            UUID.fromString(row[0] as String),
            (row[1] as Timestamp).toLocalDateTime().toLocalDate(),
            (row[4] as Timestamp).toLocalDateTime().toLocalDate(),
            ofNullable(row[2])
                .map { (it as Timestamp).toLocalDateTime().toLocalDate().plusDays(1) } // the day after billing enddate
                .orElse((row[1] as Timestamp).toLocalDateTime().toLocalDate()), // contract start date
            ProductId.valueOf(row[3] as String),
            BillingDefinitions.get(ProductId.valueOf(row[3] as String))
        )
        constructor(toBill: ToBill, basePeriodsToPrice: List<Period>, periodsToBill: List<BillPeriod>): this(
            toBill.contractId,
            toBill.contractStart,
            toBill.contractEnd,
            toBill.billFrom,
            toBill.productId,
            toBill.billingDefinition,
            basePeriodsToPrice,
            periodsToBill
        )
    }
}

data class RecurringBillingJob(val jobId: UUID, val numSelectedContracts: Int, val numGroups: Int)
