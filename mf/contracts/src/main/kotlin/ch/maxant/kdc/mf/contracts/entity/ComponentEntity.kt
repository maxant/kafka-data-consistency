package ch.maxant.kdc.mf.contracts.entity

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_COMPONENTS")
@NamedQueries(
        NamedQuery(name = ComponentEntity.NqSelectByContractId.name, query = ComponentEntity.NqSelectByContractId.query)
)
open class ComponentEntity( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "PARENT_ID", nullable = true, updatable = false)
    @Type(type = "uuid-char")
    var parentId: UUID?,

    @Column(name = "CONTRACT_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var contractId: UUID,

    @Column(name = "CONFIGURATION", nullable = false)
    var configuration: String,

    @Column(name = "COMPONENTDEFINITION_ID", nullable = false)
    var componentDefinitionId: String

) {

    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{}", "Milkshake")

    @Column(name = "PRODUCT_ID")
    @Enumerated(EnumType.STRING)
    var productId: ProductId? = null

    object NqSelectByContractId {
        const val name = "selectComponentByContractId"
        const val contractIdParam = "contractId"
        const val query = "from ComponentEntity c where c.contractId = :$contractIdParam"
    }

    object Queries {
        fun selectByContractId(em: EntityManager, contractId: UUID): List<ComponentEntity> {
            return em.createNamedQuery(NqSelectByContractId.name, ComponentEntity::class.java)
                    .setParameter(NqSelectByContractId.contractIdParam, contractId)
                    .resultList
        }
    }
}