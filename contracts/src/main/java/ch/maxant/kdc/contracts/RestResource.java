package ch.maxant.kdc.contracts;

import ch.maxant.kdc.products.HomeContentsInsurance;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;

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

    /** fetches all versions, without product details */
    @GET
    @Path("versions/{contractNumber}")
    public Response getContractVersionsByContractNumber(@PathParam("contractNumber") String contractNumber) {
        return Response.ok(
                em.createQuery("select c from Contract c where c.contractNumber = :cn")
                        .setParameter("cn", contractNumber)
                        .getResultList()
        ).build();
    }

    /** fetches a specific version, with product details */
    @GET
    @Path("{id}")
    public Response getContractById(@PathParam("id") UUID id) {
        Contract contract = em.find(Contract.class, id);
        contract.getProduct(); // lazy load
        return Response.ok(contract).build();
    }

    @POST
    @Transactional
    @Path("/{productClass}")
    public Response createContract(@PathParam("productClass") String productClass, Contract contract) {
        if(HomeContentsInsurance.class.getSimpleName().equals(productClass)) {
            HomeContentsInsurance product = new HomeContentsInsurance();
            // set some defaults
            product.setDiscount(BigDecimal.ZERO);
            product.setTotalInsuredValue(new BigDecimal("100000.00"));
            product.setTo(contract.getTo());
            product.setFrom(contract.getFrom());
            em.persist(product);

            // attach product to contract
            contract.setProduct(product);
            em.persist(contract);
        } else {
            return Response.status(BAD_REQUEST.getStatusCode(), "unknown product class " + productClass).build();
        }
        return Response.ok(contract).build();
    }

    @PUT
    @Transactional
    public Response updateContract(Contract contract) {
        try {
            contract = em.merge(contract);
            return Response.ok(contract).build();
        } catch (OptimisticLockException e) {
            return Response.status(CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }
}