package ch.maxant.kdc.contracts;

import ch.maxant.kdc.products.HomeContentsInsurance;
import ch.maxant.kdc.products.Product;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;

@Path("/contracts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RestResource {

    private static final LocalDateTime HIGH_DATE = LocalDateTime.parse("9999-12-31T23:59:59.999");

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
        List<Contract> contractVersions = em.createQuery("select c from Contract c where c.contractNumber = :cn")
                .setParameter("cn", contractNumber)
                .getResultList();
        return Response.ok(contractVersions).build();
    }

    @GET
    @Path("{id}")
    public Response getContractById(@PathParam("id") UUID id) {
        Contract contract = em.find(Contract.class, id);
        return Response.ok(contract).build();
    }

    @POST
    @Transactional
    @Path("/{productClass}")
    public Response createContract(@PathParam("productClass") String productClass, Contract contract) {
        if(HomeContentsInsurance.class.getSimpleName().equals(productClass)) {
            em.persist(contract);

            HomeContentsInsurance product = new HomeContentsInsurance();
            product.setContractId(contract.getId());
            // set some defaults
            product.setDiscount(BigDecimal.ZERO);
            product.setTotalInsuredValue(new BigDecimal("100000.00"));
            product.setTo(contract.getTo());
            product.setFrom(contract.getFrom());
            em.persist(product);
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

    @PUT
    @Transactional
    @Path("/totalInsuredValue")
    public Response updateTotalInsuredValue(UpdateTotalInsuredValueRequest request) {

        Product product = em.createQuery("select p from Product p where p.contractId = :cid and p.from <= :date and p.to >= :date", Product.class)
                .setParameter("cid", request.getContractId())
                .setParameter("date", request.getFrom())
                .getSingleResult();

        // TODO add some date validation

        product.setTo(request.getFrom().minus(1, ChronoUnit.MILLIS));

        if(product instanceof HomeContentsInsurance) {
            HomeContentsInsurance replacement = new HomeContentsInsurance();
            replacement.setContractId(product.getContractId());
            replacement.setFrom(request.getFrom());
            replacement.setTo(HIGH_DATE); // TODO for back dating, we need to actually get the from of the current replacement
            replacement.setTotalInsuredValue(request.getNewTotalInsuredValue());
            replacement.setDiscount(product.getDiscount());
            em.persist(replacement);

// OK, because we are doing this at attribute level, it seems to atually work?!
// but it isn't actually in 3NF because we are duplicating loads of data

// TODO does this actually work as expected?
        } else {
            throw new RuntimeException("unknown product type " + product.getClass().getSimpleName());
        }

        return Response.noContent().build();
    }
}