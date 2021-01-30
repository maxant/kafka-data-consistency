package ch.maxant.kdc.mf.billing.entity

import org.hibernate.annotations.Type
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDate
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

    @Column(name = "STARTDATE", nullable = false)
    open var start: LocalDate,

    @Column(name = "ENDDATE", nullable = false)
    open var end: LocalDate,

    @Column(name = "PRICE", nullable = false)
    var price: BigDecimal,

    @Column(name = "CREATED_AT", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", LocalDate.MIN, LocalDate.MAX, BigDecimal.ZERO)

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
