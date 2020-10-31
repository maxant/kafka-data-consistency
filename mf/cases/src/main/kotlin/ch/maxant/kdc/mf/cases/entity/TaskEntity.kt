package ch.maxant.kdc.mf.cases.entity

import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_TASKS")
class TaskEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "CASE_ID", nullable = false, updatable = false)
    @Type(type = "uuid-char")
    var caseId: UUID,

    @Column(name = "USER_ID", nullable = false)
    var userId: String,

    @Column(name = "TITLE", nullable = false)
    var title: String,

    @Column(name = "DESCRIPTION", nullable = false)
    var description: String,

    @Column(name = "Status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: Status

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", "", "", Status.OPEN)

    object NqSelectByCaseId {
        const val name = "selectByCaseId"
        const val caseIdParam = "caseId"
        const val query = "from TaskEntity t where t.caseId = :" + caseIdParam
    }

    object Queries {
        fun selectByCaseId(em: EntityManager, caseId: UUID): MutableList<TaskEntity> {
            return em.createNamedQuery(NqSelectByCaseId.name, TaskEntity::class.java)
                    .setParameter(NqSelectByCaseId.caseIdParam, caseId)
                    .resultList
        }

        fun selectByTaskId(em: EntityManager, taskId: UUID): TaskEntity {
            return em.find(TaskEntity::class.java, taskId)
        }
    }

}

enum class Status {
    OPEN,

    DONE
}