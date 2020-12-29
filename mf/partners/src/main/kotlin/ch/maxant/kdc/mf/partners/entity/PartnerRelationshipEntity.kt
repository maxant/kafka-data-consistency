package ch.maxant.kdc.mf.partners.entity

import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_PARTNER_RELATIONSHIPS")
@NamedQueries(
        NamedQuery(name = PartnerRelationshipEntity.NqSelectByForeignIdAndRole.name, query = PartnerRelationshipEntity.NqSelectByForeignIdAndRole.query),
        NamedQuery(name = PartnerRelationshipEntity.NqSelectByForeignId.name, query = PartnerRelationshipEntity.NqSelectByForeignId.query),
        NamedQuery(name = PartnerRelationshipEntity.NqSelectByPartnerId.name, query = PartnerRelationshipEntity.NqSelectByPartnerId.query)
)
class PartnerRelationshipEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "PARTNER_ID")
    @Type(type = "uuid-char")
    var partnerId: UUID = UUID.randomUUID(),

    @Column(name = "FOREIGN_ID", nullable = false)
    var foreignId: String,

    @Column(name = "STARTTIME", nullable = false)
    open var start: LocalDateTime,

    @Column(name = "ENDTIME", nullable = false)
    open var end: LocalDateTime,

    @Column(name = "ROLE", nullable = false)
    @Enumerated(EnumType.STRING)
    var role: Role
) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", LocalDateTime.MIN, LocalDateTime.MAX, Role.CONTRACT_HOLDER)

    object NqSelectByForeignIdAndRole {
        const val name = "selectPartnerRelationshipsByForeignIdAndRole"
        const val foreignIdParam = "foreignId"
        const val roleParam = "role"
        const val query = """
                from PartnerRelationshipEntity p
                where p.foreignId = :$foreignIdParam
                  and p.role = :$roleParam
                """
    }

    object NqSelectByForeignId {
        const val name = "selectPartnerRelationshipsByForeignId"
        const val foreignIdParam = "foreignId"
        const val query = """
                from PartnerRelationshipEntity p
                where p.foreignId = :$foreignIdParam
                """
    }

    object NqSelectByPartnerId {
        const val name = "selectPartnerRelationshipsByPartnerId"
        const val partnerIdParam = "partnerId"
        const val query = """
                from PartnerRelationshipEntity p
                where p.partnerId = :$partnerIdParam
                """
    }

    object Queries {
        fun selectByForeignIdAndRole(em: EntityManager, foreignId: String, role: Role): List<PartnerRelationshipEntity> {
            return em.createNamedQuery(NqSelectByForeignIdAndRole.name, PartnerRelationshipEntity::class.java)
                    .setParameter(NqSelectByForeignIdAndRole.foreignIdParam, foreignId)
                    .setParameter(NqSelectByForeignIdAndRole.roleParam, role)
                    .resultList
        }

        fun selectByForeignId(em: EntityManager, foreignId: String): List<PartnerRelationshipEntity> {
            return em.createNamedQuery(NqSelectByForeignId.name, PartnerRelationshipEntity::class.java)
                    .setParameter(NqSelectByForeignId.foreignIdParam, foreignId)
                    .resultList
        }

        fun selectByPartnerId(em: EntityManager, partnerId: UUID): List<PartnerRelationshipEntity> {
            return em.createNamedQuery(NqSelectByPartnerId.name, PartnerRelationshipEntity::class.java)
                    .setParameter(NqSelectByPartnerId.partnerIdParam, partnerId)
                    .resultList
        }
    }
}

// currently every role exists just once at a time.
// if that was to increase, we'd have to adjust the validation which uses "latest" semantics!
enum class Role(val minCardinality: Int, val maxCardinality: Int, val foreignIdType: ForeignIdType) {
    CONTRACT_HOLDER(1, 1, ForeignIdType.CONTRACT),
    INVOICE_RECIPIENT(1, Int.MAX_VALUE, ForeignIdType.CONTRACT),
    SALES_REP(1, Int.MAX_VALUE, ForeignIdType.CONTRACT),
    MARKETING_RECIPIENT(0, Int.MAX_VALUE, ForeignIdType.CONTRACT),
    ORDER_RECIPIENT(1, Int.MAX_VALUE, ForeignIdType.ORDER),
    SUPPLIER(1, Int.MAX_VALUE, ForeignIdType.ADDITIONAL_INFORMATION),
}

enum class ForeignIdType {
    CONTRACT,
    ORDER,
    ADDITIONAL_INFORMATION
}