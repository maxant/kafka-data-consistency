package ch.maxant.kdc.mf.contracts.entity

import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "T_CONTRACTS")
class Contract {
    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    private var id: UUID = UUID.randomUUID()
}