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
                query = PriceEntity.NqDeleteByContractId.query)
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
    var tax: BigDecimal
) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusDays(300), UUID.randomUUID(), "", BigDecimal.TEN, BigDecimal.ONE)

    object NqDeleteByContractId {
        const val name = "deletePriceByContractId"
        const val contractIdParam = "contractId"
        const val query = "delete from PriceEntity p where p.contractId = :$contractIdParam"
    }

    object Queries {
        fun deleteByContractId(em: EntityManager, contractId: UUID): Int {
            return em.createNamedQuery(NqDeleteByContractId.name)
                    .setParameter(NqDeleteByContractId.contractIdParam, contractId)
                    .executeUpdate()
        }
    }
}