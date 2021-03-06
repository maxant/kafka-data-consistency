package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.control.CaseChangedEvent
import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
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
    @Operation(summary = "get all cases including tasks, by referenceIds",
            description = "taskState should be one of *, OPEN or DONE. only tasks matching, are returned.")
    @Path("/byReferenceIds/{taskState}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getByReferenceIds(@Parameter(name = "taskState") @PathParam("taskState") taskStateString: String,
                          @Parameter(name = "referenceIds") @QueryParam("referenceIds") referenceIds: List<UUID>): Response {
        val cases = CaseEntity.Queries.selectByReferenceIds(em, referenceIds)
        val tasks = TaskEntity.Queries.selectByCaseIds(em, cases.map { it.id })
                .filter { taskStateString == "*" || State.valueOf(taskStateString) == it.state }

        val results = cases.map { CaseChangedEvent(it, tasks.filter { task -> task.caseId == it.id }) }
        return Response.ok(results).build()
    }

}
