package ch.maxant.kdc.mf.contracts.entity

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_COMPONENTS")
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

}