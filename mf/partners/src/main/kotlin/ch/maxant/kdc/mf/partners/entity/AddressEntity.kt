package ch.maxant.kdc.mf.partners.entity

import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_ADDRESSES")
class AddressEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "PARTNER_ID", updatable = false, nullable = false)
    @Type(type = "uuid-char")
    var partnerId: UUID,

    @Column(name = "STREET", nullable = false)
    var street: String,

    @Column(name = "HOUSE_NUMBER", nullable = false)
    var houseNumber: String,

    @Column(name = "POSTCODE", nullable = false)
    var postcode: String,

    @Column(name = "CITY", nullable = false)
    var city: String,

    @Column(name = "STATE", nullable = false)
    var state: String,

    @Column(name = "COUNTRY", nullable = false)
    var country: String,

    @Column(name = "ADDRESS_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    var type: AddressType

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", "", "", "", "", "", AddressType.PRIMARY)
}

enum class AddressType {
    PRIMARY,
    SECONDARY
}