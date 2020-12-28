package ch.maxant.kdc.mf.cases.entity

import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "T_TASKS")
@NamedQueries(
        NamedQuery(name = TaskEntity.NqSelectByCaseIds.name, query = TaskEntity.NqSelectByCaseIds.query)
)
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

    @Column(name = "STATE", nullable = false)
    @Enumerated(EnumType.STRING)
    var state: State

) {
    constructor() : this(UUID.randomUUID(), UUID.randomUUID(), "", "", "", State.OPEN)

    object NqSelectByCaseIds {
        const val name = "selectTaskByCaseIds"
        const val caseIdsParam = "caseIds"
        const val query = "from TaskEntity t where t.caseId in :$caseIdsParam"
    }

    object Queries {
        fun selectByCaseId(em: EntityManager, caseId: UUID) = selectByCaseIds(em, listOf(caseId))

        fun selectByCaseIds(em: EntityManager, caseIds: List<UUID>): MutableList<TaskEntity> {
            return em.createNamedQuery(NqSelectByCaseIds.name, TaskEntity::class.java)
                    .setParameter(NqSelectByCaseIds.caseIdsParam, caseIds)
                    .resultList
        }

        fun selectByTaskId(em: EntityManager, taskId: UUID): TaskEntity {
            return em.find(TaskEntity::class.java, taskId)
        }
    }

}

enum class State {
    OPEN,

    DONE
}