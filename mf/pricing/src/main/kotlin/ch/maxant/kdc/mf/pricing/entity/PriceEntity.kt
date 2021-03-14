package ch.maxant.kdc.mf.pricing.entity

import org.hibernate.annotations.Type
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_PRICES")
@NamedQueries(
        NamedQuery(name = PriceEntity.NqDeleteByContractId.name,
                query = PriceEntity.NqDeleteByContractId.query),
        NamedQuery(name = PriceEntity.NqCountByContractIdAndNotSyncTime.name,
                query = PriceEntity.NqCountByContractIdAndNotSyncTime.query),
        NamedQuery(name = PriceEntity.NqSelectByComponentIdsAndDateTime.name,
                query = PriceEntity.NqSelectByComponentIdsAndDateTime.query),
        NamedQuery(name = PriceEntity.NqSelectByContractIdAndDateTime.name,
                query = PriceEntity.NqSelectByContractIdAndDateTime.query),
        NamedQuery(name = PriceEntity.NqSelectByContractIdsOrderedByStartAsc.name,
                query = PriceEntity.NqSelectByContractIdsOrderedByStartAsc.query)
)
class PriceEntity( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "CONTRACT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var contractId: UUID,

    @Column(name = "STARTTIME", nullable = false)
    open var start: LocalDateTime,

    @Column(name = "ENDTIME", nullable = false)
    open var end: LocalDateTime,

    @Column(name = "COMPONENT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var componentId: UUID,

    @Column(name = "PRICING_ID", nullable = false, updatable = false)
    var pricingId: String,

    @Column(name = "PRICE", nullable = false)
    var price: BigDecimal,

    @Column(name = "TAX", nullable = false)
    var tax: BigDecimal,

    @Column(name = "SYNC_TIMESTAMP", nullable = false)
    open var syncTimestamp: Long
) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusDays(300), UUID.randomUUID(), "", BigDecimal.TEN, BigDecimal.ONE, 0)

    object NqDeleteByContractId {
        const val name = "deletePriceByContractId"
        const val contractIdParam = "contractId"
        const val query = "delete from PriceEntity p where p.contractId = :$contractIdParam"
    }

    object NqCountByContractIdAndNotSyncTime {
        const val name = "countByContractIdAndNotSyncTime"
        const val contractIdParam = "contractId"
        const val syncTimestampParam = "syncTimestamp"
        const val query = """
            select count(p) 
            from PriceEntity p 
            where p.contractId = :$contractIdParam
              and p.syncTimestamp <> :$syncTimestampParam
            """
    }

    object NqSelectByComponentIdsAndDateTime {
        const val name = "selectByComponentIdsAndDateTime"
        const val componentIdsParam = "componentIds"
        const val dateTimeParam = "dateTime"
        const val query = """
            select p 
            from PriceEntity p 
            where p.componentId in :$componentIdsParam
              and p.start <= :$dateTimeParam
              and p.end >= :$dateTimeParam
            """
    }

    object NqSelectByContractIdAndDateTime {
        const val name = "selectByContractIdAndDateTime"
        const val contractIdParam = "contractId"
        const val dateTimeParam = "dateTime"
        const val query = """
            select p 
            from PriceEntity p 
            where p.contractId in :$contractIdParam
              and p.start <= :$dateTimeParam
              and p.end >= :$dateTimeParam
            """
    }

    object NqSelectByContractIdsOrderedByStartAsc {
        const val name = "selectByContractIds"
        const val contractIdsParam = "contractIds"
        const val query = """
            select p 
            from PriceEntity p 
            where p.contractId in :$contractIdsParam
            order by p.start asc
            """
    }

    object Queries {
        private val log = Logger.getLogger(this.javaClass)

        fun deleteByContractId(em: EntityManager, contractId: UUID): Int {
            return em.createNamedQuery(NqDeleteByContractId.name)
                    .setParameter(NqDeleteByContractId.contractIdParam, contractId)
                    .executeUpdate()
        }

        // TODO is there a nicer way to deal with jpa requiring us to return a long here?
        fun countByContractIdAndNotSyncTimestamp(em: EntityManager, contractId: UUID, syncTimestamp: Long): Long {
            log.info("counting for contract $contractId and syncTimestamp $syncTimestamp")
            return em.createNamedQuery(NqCountByContractIdAndNotSyncTime.name, java.lang.Long::class.java)
                    .setParameter(NqCountByContractIdAndNotSyncTime.contractIdParam, contractId)
                    .setParameter(NqCountByContractIdAndNotSyncTime.syncTimestampParam, syncTimestamp)
                    .singleResult.toLong()
        }

        fun selectByComponentIdsAndDateTime(em: EntityManager, componentIds: List<UUID>, dateTime: LocalDateTime): List<PriceEntity> {
            log.info("getting price entities for components $componentIds and dateTime $dateTime")
            return em.createNamedQuery(NqSelectByComponentIdsAndDateTime.name, PriceEntity::class.java)
                    .setParameter(NqSelectByComponentIdsAndDateTime.componentIdsParam, componentIds)
                    .setParameter(NqSelectByComponentIdsAndDateTime.dateTimeParam, dateTime)
                    .resultList
        }

        fun selectByContractIdAndDateTime(em: EntityManager, contractId: UUID, dateTime: LocalDateTime): List<PriceEntity> {
            log.info("getting price entities for contract $contractId and dateTime $dateTime")
            return em.createNamedQuery(NqSelectByContractIdAndDateTime.name, PriceEntity::class.java)
                    .setParameter(NqSelectByContractIdAndDateTime.contractIdParam, contractId)
                    .setParameter(NqSelectByComponentIdsAndDateTime.dateTimeParam, dateTime)
                    .resultList
        }

        fun selectByContractIdsOrderedByStart(em: EntityManager, contractIds: List<UUID>): List<PriceEntity> {
            log.info("getting price entities for contracts $contractIds")
            return em.createNamedQuery(NqSelectByContractIdsOrderedByStartAsc.name, PriceEntity::class.java)
                    .setParameter(NqSelectByContractIdsOrderedByStartAsc.contractIdsParam, contractIds)
                    .resultList
        }
    }
}