package ch.maxant.kdc.mf.contracts.entity

import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_CONTRACTS")
open class ContractEntity( // add open, rather than rely on maven plugin, because @QuarkusTest running in IntelliJ seems to think its final

        @Id
        @Column(name = "ID")
        @Type(type = "uuid-char")
        open var id: UUID,

        @Column(name = "PRODUCT_ID")
        open var productId: ProductId,

        @Column(name = "STARTTIME")
        open var start: LocalDateTime,

        @Column(name = "ENDTIME")
        open var end: LocalDateTime,

        @Column(name = "STATUS")
        open var status: Status

) {
    constructor() : this(UUID.randomUUID(), ProductId.CHOCOLATE_BASIC, LocalDateTime.MIN, LocalDateTime.MAX, Status.DRAFT)
}