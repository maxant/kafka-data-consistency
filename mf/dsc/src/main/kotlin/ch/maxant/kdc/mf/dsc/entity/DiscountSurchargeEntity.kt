package ch.maxant.kdc.mf.dsc.entity

import org.hibernate.annotations.Type
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_DISCOUNTS_SURCHARGES")
@NamedQueries(
    NamedQuery(name = DiscountSurchargeEntity.NqCountByContractIdAndNotSyncTime.name,
        query = DiscountSurchargeEntity.NqCountByContractIdAndNotSyncTime.query),
    NamedQuery(name = DiscountSurchargeEntity.NqDeleteByContractIdAndNotAddedManually.name,
        query = DiscountSurchargeEntity.NqDeleteByContractIdAndNotAddedManually.query),
    NamedQuery(name = DiscountSurchargeEntity.NqFindByContractId.name,
        query = DiscountSurchargeEntity.NqFindByContractId.query)
)
class DiscountSurchargeEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "CONTRACT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var contractId: UUID,

    @Column(name = "COMPONENT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var componentId: UUID,

    @Column(name = "DEFINITION_ID", nullable = false, updatable = false)
    var definitionId: String,

    @Column(name = "VALUE", nullable = false, updatable = false)
    var value: BigDecimal,

    @Column(name = "SYNC_TIMESTAMP", nullable = false)
    var syncTimestamp: Long,

    @Column(name = "ADDED_MANUALLY", nullable = false)
    var addedManually: Boolean = false

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "", BigDecimal.ONE, 0)

    object NqCountByContractIdAndNotSyncTime {
        const val name = "countDiscountsSurchargesByContractIdAndNotSyncTime"
        const val contractIdParam = "contractId"
        const val syncTimestampParam = "syncTimestamp"
        const val query = """
            select count(e) 
            from DiscountSurchargeEntity e 
            where e.contractId = :$contractIdParam
              and e.syncTimestamp <> :$syncTimestampParam
            """
    }

    object NqDeleteByContractIdAndNotAddedManually {
        const val name = "deleteDiscountsSurchargesByContractIdAndNotAddedManually"
        const val contractIdParam = "contractId"
        const val query = """
            delete
            from DiscountSurchargeEntity e 
            where e.contractId = :$contractIdParam
              and e.addedManually = false
            """
    }

    object NqFindByContractId {
        const val name = "findDiscountsSurchargesByContractId"
        const val contractIdParam = "contractId"
        const val query = """
            select e 
            from DiscountSurchargeEntity e 
            where e.contractId = :$contractIdParam
            """
    }

    object Queries {
        private val log = Logger.getLogger(this.javaClass)

        fun countByContractIdAndNotSyncTimestamp(em: EntityManager, contractId: UUID, syncTimestamp: Long): Long {
            log.info("counting for contract $contractId and syncTimestamp $syncTimestamp")
            return em.createNamedQuery(NqCountByContractIdAndNotSyncTime.name, java.lang.Long::class.java)
                .setParameter(NqCountByContractIdAndNotSyncTime.contractIdParam, contractId)
                .setParameter(NqCountByContractIdAndNotSyncTime.syncTimestampParam, syncTimestamp)
                .singleResult.toLong()
        }

        fun deleteByContractIdAndNotAddedManually(em: EntityManager, contractId: UUID): Int {
            log.info("deleting for contract $contractId and not added manually")
            return em.createNamedQuery(NqDeleteByContractIdAndNotAddedManually.name)
                .setParameter(NqDeleteByContractIdAndNotAddedManually.contractIdParam, contractId)
                .executeUpdate()
        }

        fun findByContractId(em: EntityManager, contractId: UUID): List<DiscountSurchargeEntity> {
            log.info("finding for contract $contractId")
            return em.createNamedQuery(NqFindByContractId.name, DiscountSurchargeEntity::class.java)
                .setParameter(NqFindByContractId.contractIdParam, contractId)
                .resultList
        }
    }
}
