package ch.maxant.kdc.mf.contracts.entity

import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_CONTRACTS")
open class ContractEntity( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

        @Id
        @Column(name = "ID")
        @Type(type = "uuid-char")
        open var id: UUID,

        @Column(name = "STARTTIME", nullable = false)
        open var start: LocalDateTime,

        @Column(name = "ENDTIME", nullable = false)
        open var end: LocalDateTime,

        @Column(name = "STATE", nullable = false)
        @Enumerated(EnumType.STRING)
        open var contractState: ContractState

) {
    // for hibernate
    constructor() : this(UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MAX, ContractState.DRAFT)
}