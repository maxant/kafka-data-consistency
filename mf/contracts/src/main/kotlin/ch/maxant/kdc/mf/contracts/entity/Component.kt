package ch.maxant.kdc.mf.contracts.entity

import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_COMPONENTS")
open class Component( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    private var id: UUID,

    @Column(name = "PARENT_ID")
    private var parentId: UUID,

    @Column(name = "PRODUCTCOMPONENT_ID")
    private var productComponentId: String,

    @Column(name = "CONTRACT_ID")
    private var contractId: UUID,

    @Column(name = "CONFIGURATION")
    private var configuration: String

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", UUID.randomUUID(), "")
}