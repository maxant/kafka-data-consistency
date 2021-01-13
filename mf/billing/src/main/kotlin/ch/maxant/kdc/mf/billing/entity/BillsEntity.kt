package ch.maxant.kdc.mf.billing.entity

import org.hibernate.annotations.Type
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_BILLS")
@NamedQueries(
        NamedQuery(name = BillsEntity.NqSelectByContractIds.name,
                query = BillsEntity.NqSelectByContractIds.query)
)
class BillsEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "CONTRACT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var contractId: UUID,

    @Column(name = "DEFINITION_ID", nullable = false, updatable = false)
    var definitionId: String,

    @Column(name = "STARTTIME", nullable = false)
    open var start: LocalDateTime,

    @Column(name = "ENDTIME", nullable = false)
    open var end: LocalDateTime,

    @Column(name = "PRICE", nullable = false)
    var price: BigDecimal

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", LocalDateTime.MIN, LocalDateTime.MAX, BigDecimal.ZERO)

    object NqSelectByContractIds {
        const val name = "selectByContractIds"
        const val contractIdsParam = "contractIds"
        const val query = """
            select b 
            from BillsEntity b 
            where b.contractId in :$contractIdsParam
            """
    }

    object Queries {
        private val log = Logger.getLogger(this.javaClass)

        fun selectByContractIds(em: EntityManager, contractIds: List<UUID>): List<BillsEntity> {
            log.info("getting billing entities for contracts $contractIds")
            return em.createNamedQuery(NqSelectByContractIds.name, BillsEntity::class.java)
                    .setParameter(NqSelectByContractIds.contractIdsParam, contractIds)
                    .resultList
        }
    }
}
