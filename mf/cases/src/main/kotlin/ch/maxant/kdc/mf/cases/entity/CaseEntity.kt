package ch.maxant.kdc.mf.cases.entity

import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_CASES")
@NamedQueries(
    NamedQuery(name = CaseEntity.NqSelectByReferenceId.name, query = CaseEntity.NqSelectByReferenceId.query)
)
class CaseEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "REFERENCE_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var referenceId: UUID = UUID.randomUUID(),

    @Column(name = "TYPE", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    var type: CaseType

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), CaseType.SALES)

    object NqSelectByReferenceId {
        const val name = "selectByReferenceId"
        const val referenceIdParam = "referenceId"
        const val query = "from CaseEntity c where c.referenceId = :" + referenceIdParam
    }

    object Queries {
        fun selectByReferenceId(em: EntityManager, referenceId: UUID): CaseEntity {
            return em.createNamedQuery(NqSelectByReferenceId.name, CaseEntity::class.java)
                    .setParameter(NqSelectByReferenceId.referenceIdParam, referenceId)
                    .singleResult
        }

        fun selectByCaseId(em: EntityManager, caseId: UUID): CaseEntity {
            return em.find(CaseEntity::class.java, caseId)
        }
    }
}

enum class CaseType {
    /** when we sell something */
    SALES,

    /** when we purchase something */
    PURCHASE,

    /** when a customer stakes a claim on a product for which they
     * have a subscription based contract - see TMF README.md */
    CLAIM

}

