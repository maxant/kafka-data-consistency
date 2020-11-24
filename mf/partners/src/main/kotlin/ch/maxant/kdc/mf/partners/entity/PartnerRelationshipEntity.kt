package ch.maxant.kdc.mf.partners.entity

import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_PARTNER_RELATIONSHIPS")
@NamedQueries(
        NamedQuery(name = PartnerRelationshipEntity.NqSelectByForeignIdAndRole.name, query = PartnerRelationshipEntity.NqSelectByForeignIdAndRole.query)
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

    object Queries {
        fun selectByForeignIdAndRole(em: EntityManager, foreignId: String, role: Role): List<PartnerRelationshipEntity> {
            return em.createNamedQuery(NqSelectByForeignIdAndRole.name, PartnerRelationshipEntity::class.java)
                    .setParameter(NqSelectByForeignIdAndRole.foreignIdParam, foreignId)
                    .setParameter(NqSelectByForeignIdAndRole.roleParam, role)
                    .resultList
        }
    }
}

enum class Role {
    CONTRACT_HOLDER,
    SALES_REP
}