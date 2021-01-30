package ch.maxant.kdc.mf.billing.entity

import org.hibernate.annotations.Type
import java.time.LocalDate
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_BILLED_TO")
class BilledToEntity(

    @Id
    @Column(name = "CONTRACT_ID")
    @Type(type = "uuid-char")
    var id: UUID,

    @Column(name = "BILLED_TO", nullable = false)
    open var billedTo: LocalDate
) {
    constructor() : this(UUID.randomUUID(), LocalDate.MAX)
}
