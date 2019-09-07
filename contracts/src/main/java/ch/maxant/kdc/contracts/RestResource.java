package ch.maxant.kdc.contracts;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/contracts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RestResource {

    @Inject
    EntityManager em;

    @GET
    @Path("dbversion")
    public Response getDbs() {
        return Response.ok(em.createNativeQuery("select version from flyway_schema_history").getSingleResult()).build();
    }

    @GET
    @Path("versions/{contractNumber}")
    public Response getContractVersions(@PathParam("contractNumber") String contractNumber) {
        return Response.ok(
                em.createQuery("select c from Contract c where c.contractNumber = :cn")
                        .setParameter("cn", contractNumber)
                        .getResultList()
        ).build();
    }

    @GET
    @Path("{id}")
    public Response getContractVersion(@PathParam("id") UUID id) {
        return Response.ok(em.find(Contract.class, id)).build();
    }

    @POST
    @Transactional
    public Response createContract(Contract contract) {
        em.persist(contract);
        return Response.ok(contract).build();
    }

    @PUT
    @Transactional
    public Response updateContract(Contract contract) {
        try {
            contract = em.merge(contract);
            return Response.ok(contract).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }
}