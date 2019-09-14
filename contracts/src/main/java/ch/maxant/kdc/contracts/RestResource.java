package ch.maxant.kdc.contracts;

import ch.maxant.kdc.products.HomeContentsInsurance;
import ch.maxant.kdc.products.Product;
import ch.maxant.kdc.products.WithTotalInsuredValue;
import ch.maxant.kdc.products.WithValidity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
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
    @Path("/dbversion")
    public Response getDbs() {
        return Response.ok(em.createNativeQuery("select max(version) from flyway_schema_history").getSingleResult()).build();
    }

    /** see README. this method provides the caller with the versions of a contract.*/
    @GET
    @Path("/versions/{contractNumber}")
    public Response getContractVersions(@PathParam("contractNumber") String contractNumber) {
        List<Contract> contractVersions = em.createQuery("select c from Contract c where c.contractNumber = :cn")
                .setParameter("cn", contractNumber)
                .getResultList();
        return Response.ok(contractVersions).build();
    }

    @POST
    @Transactional
    @Path("/{productClass}")
    public Response createContract(@PathParam("productClass") String productClass, Contract contract) {

        // TODO validate that there is no other contract which overlaps this period with the same number!
        // if there is, the caller needs to first update existing ones, and make space!

        if (HomeContentsInsurance.class.getSimpleName().equals(productClass)) {
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
            final Contract cf = contract;
            fixProductInstanceVersions(contract, cf);

            return Response.ok(contract).build();
        } catch (OptimisticLockException e) {
            return Response.status(CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }

    /** now update/delete product instance versions */
    private void fixProductInstanceVersions(Contract contract, Contract cf) {
        List<Product> products = em.createQuery("select p from Product p where p.contractId = :cid", Product.class)
                .setParameter("cid", contract.getId())
                .getResultList();

        // CASE 1: we could have this:
        //
        // |--A--|--B--|--C--|--D--|--E--|
        //          ^from       ^to
        //
        // A and E need to be deleted.
        // B and D need to be adjusted.
        // C can be ignored.
        //
        // CASE 2: but we could also have this, at either end:
        //
        //    |--A--|--B--|
        //  ^from           ^to
        //
        // A needs to be adjusted
        //
        // CASE 3: and of course a combination of cases 1 & 2... so handle "from" and then handle "to".

        // if there is nothing before the "from", then its case 2 so adjust the first one, otherwise its case 1.
        if(products.stream().allMatch(p -> cf.getFrom().isBefore(p.getFrom()))) {
            // case 2 for "from" => adjust first one
            products.stream().sorted(Comparator.comparing(Product::getFrom)).findFirst().get().setFrom(cf.getFrom());
        } else {
            // case 1 for "from". so i) delete anything ending before "from" and ii) adjust "from" for anything overlapping "from"
            products.stream().filter(p -> p.getTo().isBefore(cf.getFrom())).forEach(p -> em.remove(p));
            products.stream().filter(p -> matches(cf.getFrom(), p)).forEach(p -> p.setFrom(cf.getFrom()));
        }

        // if there is nothing after the "to", then its case 2 so adjust the last one, otherwise its case 1.
        if(products.stream().allMatch(p -> cf.getTo().isAfter(p.getTo()))) {
            // case 2 for "to" => adjust first one in reversed list
            products.stream().sorted(Comparator.comparing(Product::getTo).reversed()).findFirst().get().setTo(cf.getTo());
        } else {
            // case 1 for "to". so i) delete anything starting after "to" and ii) adjust "to" for anything overlapping "to"
            products.stream().filter(p -> p.getFrom().isAfter(cf.getTo())).forEach(p -> em.remove(p));
            products.stream().filter(p -> matches(cf.getTo(), p)).forEach(p -> p.setTo(cf.getTo()));
        }
    }

    /** @return true when timestamp lies within (including equal) validity of the thing */
    private boolean matches(LocalDateTime timestamp, WithValidity thing) {
        return timestamp.compareTo(thing.getFrom()) >= 0 && timestamp.compareTo(thing.getTo()) <= 0;
    }

    @GET
    @Path("/{contractNumber}/{timestamp}")
    public Response getContractWithProductInstance(@PathParam("contractNumber") String contractNumber, @PathParam("timestamp") String timestampString) {
        LocalDateTime timestamp = getTimestamp(timestampString);

        Contract c = getContractVersionByNumberAndTimestamp(contractNumber, timestamp);

        Product p =  em.createQuery("select p from Product p where p.contractId = :cid and p.from <= :ts and :ts <= p.to", Product.class)
                .setParameter("cid", c.getId())
                .setParameter("ts", timestamp)
                .getSingleResult();

        c.setProduct(p);

        return Response.ok(c).build();
    }

    /** @return a list of all product instances in all contract versions with the given contractNumber,
     * sorty by "from" date. products contain a copy of the contract. */
    @GET
    @Path("/product/versions/{contractNumber}")
    public Response getAllHistory(@PathParam("contractNumber") String contractNumber) {

        List<Contract> contracts = em.createQuery("select c from Contract c where c.contractNumber = :cn", Contract.class)
                .setParameter("cn", contractNumber)
                .getResultList();

        Map<UUID, Contract> contractsIndexedByCid = contracts.stream().collect(toMap(Contract::getId, identity()));

        List<Product> products = em.createQuery("select p from Product p join Contract c on p.contractId = c.id where c.contractNumber = :cn", Product.class)
                .setParameter("cn", contractNumber)
                .getResultList()
                .stream()
                .peek(p -> p.setContract(contractsIndexedByCid.get(p.getContractId())))
                .sorted(Comparator.comparing(Product::getFrom))
                .collect(toList());

        return Response.ok(products).build();
    }

    private Contract getContractVersionByNumberAndTimestamp(@PathParam("contractNumber") String contractNumber, LocalDateTime timestamp) {
        return em.createQuery("select c from Contract c where c.contractNumber = :cn and c.from <= :ts and :ts <= c.to", Contract.class)
                    .setParameter("cn", contractNumber)
                    .setParameter("ts", timestamp)
                    .getSingleResult();
    }

    private LocalDateTime getTimestamp(String timestampString) {
        if(timestampString == null || timestampString.trim().isEmpty()) {
            return LocalDateTime.now();
        } else {
            try {
                return LocalDateTime.parse(timestampString);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDate.parse(timestampString).atStartOfDay();
                } catch (DateTimeParseException e2) {
                    throw new IllegalArgumentException("unable to parse string as datetime or date: " + timestampString);
                }
            }
        }
    }

    @PUT
    @Transactional
    @Path("/totalInsuredValue")
    public Response updateTotalInsuredValue(UpdateTotalInsuredValueRequest request) throws Exception {
        // split something like this:
        //
        //  |-----A-----|--B--|--C--|
        //
        // into:
        //
        //  |--A--|--D--|--B--|--C--|
        //
        // which means first finding A, and shortening it, second inserting D, and then updating B's and C's value of
        // the attribute
        //
        // - select where row.from <=  request.from >= row.to
        // - D.to = A.to
        // - D.from = request.from
        // - A.to = request.from - 1ms
        // - select where row.from >= request.from and update those rows

        Contract c = getContractVersionByNumberAndTimestamp(request.getContractNumber(), request.getFrom());

        Product product = em.createQuery("select p from Product p where p.contractId = :cid and p.from <= :date and p.to >= :date", Product.class)
                .setParameter("cid", c.getId()) // MUST use ID in order to avoid updating data related to other contract versions with the same contract number!
                .setParameter("date", request.getFrom())
                .getSingleResult();

        if (!(product instanceof WithTotalInsuredValue)) {
            throw new IllegalArgumentException("Contract does not support total insurance value");
        }

        // create a new one
        Product d = product.getClass().getConstructor().newInstance();
        d.setContractId(product.getContractId());
        d.setFrom(request.getFrom());
        d.setTo(product.getTo());
        d.setDiscount(product.getDiscount());
        ((WithTotalInsuredValue) d).setTotalInsuredValue(request.getNewTotalInsuredValue());
        em.persist(d);
        System.out.println("inserted new product instance version from " + d.getFrom() + " to " + d.getTo() + " with total insured sum " + request.getNewTotalInsuredValue());

        // update validity of original
        product.setTo(request.getFrom().minus(1, ChronoUnit.MILLIS));
        System.out.println("updated original with new to date: " + product.getTo());

        // update the rest
        int updateCount = em.createQuery("update Product p " +
                                            "set p.totalInsuredValue = :totalInsuredValue " +
                                            "where p.contractId = :cid " +
                                            "and p.from >= :ts")
                .setParameter("totalInsuredValue", request.getNewTotalInsuredValue())
                .setParameter("cid", c.getId())
                .setParameter("ts", d.getTo())
                .executeUpdate();
        System.out.println("updated " + updateCount + " product instance versions with new total insured value " + request.getNewTotalInsuredValue());

        return Response.noContent().build();
    }
}