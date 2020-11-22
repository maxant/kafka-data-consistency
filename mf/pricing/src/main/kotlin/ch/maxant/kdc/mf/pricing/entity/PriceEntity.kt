package ch.maxant.kdc.mf.pricing.entity

import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_PRICES")
@NamedQueries(
        NamedQuery(name = PriceEntity.NqDeleteByContractId.name,
                query = PriceEntity.NqDeleteByContractId.query),
        NamedQuery(name = PriceEntity.NqCountByContractIdAndSyncTime.name,
                query = PriceEntity.NqCountByContractIdAndSyncTime.query)
)
open class PriceEntity( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

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

    object NqCountByContractIdAndSyncTime {
        const val name = "selectPriceByContractId"
        const val contractIdParam = "contractId"
        const val syncTimestampParam = "syncTimestamp"
        const val query = """
            select count(p) 
            from PriceEntity p 
            where p.contractId = :$contractIdParam
              and p.syncTimestamp = :$syncTimestampParam
            """
    }

    object Queries {
        fun deleteByContractId(em: EntityManager, contractId: UUID): Int {
            return em.createNamedQuery(NqDeleteByContractId.name)
                    .setParameter(NqDeleteByContractId.contractIdParam, contractId)
                    .executeUpdate()
        }

        fun countByContractIdAndSyncTimestamp(em: EntityManager, contractId: UUID, syncTimestamp: Long): Int {
            return em.createNamedQuery(NqCountByContractIdAndSyncTime.name, Int::class.java)
                    .setParameter(NqCountByContractIdAndSyncTime.contractIdParam, contractId)
                    .setParameter(NqCountByContractIdAndSyncTime.syncTimestampParam, syncTimestamp)
                    .firstResult
        }
    }
}