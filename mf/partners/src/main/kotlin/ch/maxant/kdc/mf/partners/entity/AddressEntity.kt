package ch.maxant.kdc.mf.partners.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import org.hibernate.annotations.Type
import java.time.LocalDate
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_ADDRESSES")
@NamedQueries(
        NamedQuery(name = AddressEntity.NqSelectByPartnerId.name, query = AddressEntity.NqSelectByPartnerId.query)
)
class AddressEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARTNER_ID", updatable = false, nullable = false)
    @Type(type = "uuid-char")
    @field:JsonIgnore
    var partner: PartnerEntity?,

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
    constructor() : this(UUID.randomUUID(), PartnerEntity(), "", "", "", "", "", "", AddressType.PRIMARY)

    object NqSelectByPartnerId {
        const val name = "selectAddressByPartnerId"
        const val partnerIdParam = "partnerId"
        const val query = """
                from AddressEntity a
                where a.partner.id = :$partnerIdParam
                """
    }

    object Queries {
        fun selectByPartnerId(em: EntityManager, partnerId: UUID): List<AddressEntity> {
            return em.createNamedQuery(NqSelectByPartnerId.name, AddressEntity::class.java)
                    .setParameter(NqSelectByPartnerId.partnerIdParam, partnerId)
                    .resultList
        }
    }
}

enum class AddressType {
    PRIMARY,
    SECONDARY
}