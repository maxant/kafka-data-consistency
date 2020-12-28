package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.control.CaseChangedEvent
import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/cases")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CasesResource(
    @Inject
    var em: EntityManager
) {

    @GET
    fun getByIds(@QueryParam("referenceIds") referenceIds: List<UUID>): Response {
        val cases = CaseEntity.Queries.selectByReferenceIds(em, referenceIds)
        val tasks = TaskEntity.Queries.selectByCaseIds(em, cases.map { it.id })
        val results = cases.map { CaseChangedEvent(it, tasks.filter { task -> task.caseId == it.id }) }
        return Response.ok(results).build()
    }

}
