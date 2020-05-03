package ch.maxant.kdc.contracts;

import ch.maxant.kdc.contracts.entity.Contract;
import ch.maxant.kdc.products.BuildingInsurance;
import ch.maxant.kdc.products.Product;
import ch.maxant.kdc.products.WithIndexValue;
import ch.maxant.kdc.products.WithValidity;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
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

    /** create a brand new contract */
    @POST
    @Transactional
    @Path("/{productClass}")
    public Response createContract(@PathParam("productClass") String productClass, Contract contract) {

        List<Contract> existingVersions = em.createQuery("select c from Contract c where c.contractNumber = :cn")
                .setParameter("cn", contract.getContractNumber())
                .getResultList();

        if(!existingVersions.isEmpty()) {
            throw new IllegalArgumentException("a contract with that number already exists. it must be replaced using /contracts/replace");
        }

        createContractVersion(productClass, contract, existingVersions);

        return Response.ok(contract).build();
    }

    @POST
    @Transactional
    @Path("/replace/{productClass}")
    @Operation(summary = "Replace an existing contract with a new version.",
            description = "cuts the contract which currently intersects the given contract so that it ends when the new one starts." +
                    "the input may not intersect more than one existing one. the input must start after the existing one. the existing one must be the last.")
    public Response createReplacementContract(@PathParam("productClass") String productClass, Contract contract) {

        List<Contract> existingVersions = em.createQuery("select c from Contract c where c.contractNumber = :cn")
                .setParameter("cn", contract.getContractNumber())
                .getResultList();

        List<Contract> candidatesToBeReplaced = existingVersions.stream().filter(c -> c.isOverlapping(contract)).collect(toList());

        if(candidatesToBeReplaced.size() != 1) {
            throw new IllegalArgumentException("input contract spans more than one existing contract: " + candidatesToBeReplaced);
        }
        Contract toBeReplaced = candidatesToBeReplaced.get(0);

        if(contract.getFrom().isBefore(toBeReplaced.getFrom())) {
            throw new IllegalArgumentException("input contract doesnt start on or after existing contract: " + toBeReplaced);
        }

        if(existingVersions.stream().anyMatch(c -> c.getFrom().isAfter(toBeReplaced.getFrom()))) {
            throw new IllegalArgumentException("input contract spans a contract which is not the last one");
        }

        toBeReplaced.setTo(contract.getFrom().minus(1, ChronoUnit.MILLIS));
        fixProductInstanceVersions(toBeReplaced); // cut product instance validity short, as we have just cut the contract short

        createContractVersion(productClass, contract, existingVersions);

        return Response.ok(contract).build();
    }

    private void createContractVersion(String productClass, Contract contract, List<Contract> existingVersions) {

        validateNewContract(contract, existingVersions);

        Product product;
        if (BuildingInsurance.class.getSimpleName().equals(productClass)) {
            product = new BuildingInsurance();
            // set the value to a default, unless one is supplied
            BigDecimal indexValue = new BigDecimal("100.00");
            if(contract.getProduct() != null && contract.getProduct() instanceof WithIndexValue && ((WithIndexValue) contract.getProduct()).getIndexValue() != null) {
                indexValue = ((WithIndexValue) contract.getProduct()).getIndexValue();
            }
            ((BuildingInsurance)product).setIndexValue(indexValue);
        } else {
            throw new RuntimeException("unknown product class '" + productClass + "'");
        }

        em.persist(contract);

        // set insured sum based on incoming product, if present, with fallback to max value
        if(contract.getProduct() != null) {
            product.setInsuredSum(contract.getProduct().getInsuredSum());
        }
        if(product.getInsuredSum() == null) {
            product.setInsuredSum(new BigDecimal(99_999_999));
        }

        // set up product
        product.setContractId(contract.getId());
        product.setDiscount(BigDecimal.ZERO);
        product.setTo(contract.getTo());
        product.setFrom(contract.getFrom());

        em.persist(product);

        // attach newly created product so that it can be returned to the caller
        contract.setProduct(product);

        // TODO send new version to neo4j, which will need to sort out relationships between versions!
    }

    private void validateNewContract(Contract newContract, List<Contract> existingVersions) {
        // look for overlapping contract versions with the same contract number
        List<Contract> overlaps = existingVersions.stream().filter(c -> c.isOverlapping(newContract)).collect(toList());
        if(!overlaps.isEmpty()) {
            throw new IllegalArgumentException("contract overlaps with " + overlaps.get(0));
        }

        // ensure there are no gaps either!
        List<Contract> allVersions = new ArrayList<>(existingVersions);
        allVersions.add(newContract);
        allVersions.sort(Comparator.comparing(Contract::getFrom));
        Contract previous = null;
        for(Contract current : allVersions) {
            if(previous != null) {
                if(Duration.between(previous.getTo(), current.getFrom()).getNano() != 1_000_000) { // 1ms = 1 mio ns
                    throw new IllegalArgumentException("there is no seemless continuation between " + previous + " and " + current);
                }
            }
            previous = current;
        }
    }

    @Deprecated // this is nasty, because it can be used to create gaps between contract versions! => we dont want that!
    @PUT
    @Transactional
    public Response updateContract(Contract contract) {
        try {
            contract = em.merge(contract);
            fixProductInstanceVersions(contract);

            return Response.ok(contract).build();
        } catch (OptimisticLockException e) {
            return Response.status(CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }

    /** now update/delete product instance versions */
    private void fixProductInstanceVersions(Contract contract) {
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
        if(products.stream().allMatch(p -> contract.getFrom().isBefore(p.getFrom()))) {
            // case 2 for "from" => adjust first one
            products.stream().sorted(Comparator.comparing(Product::getFrom)).findFirst().get().setFrom(contract.getFrom());
        } else {
            // case 1 for "from". so i) delete anything ending before "from" and ii) adjust "from" for anything overlapping "from"
            products.stream().filter(p -> p.getTo().isBefore(contract.getFrom())).forEach(p -> em.remove(p));
            products.stream().filter(p -> matches(contract.getFrom(), p)).forEach(p -> p.setFrom(contract.getFrom()));
        }

        // if there is nothing after the "to", then its case 2 so adjust the last one, otherwise its case 1.
        if(products.stream().allMatch(p -> contract.getTo().isAfter(p.getTo()))) {
            // case 2 for "to" => adjust first one in reversed list
            products.stream().sorted(Comparator.comparing(Product::getTo).reversed()).findFirst().get().setTo(contract.getTo());
        } else {
            // case 1 for "to". so i) delete anything starting after "to" and ii) adjust "to" for anything overlapping "to"
            products.stream().filter(p -> p.getFrom().isAfter(contract.getTo())).forEach(p -> em.remove(p));
            products.stream().filter(p -> matches(contract.getTo(), p)).forEach(p -> p.setTo(contract.getTo()));
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
                .sorted(Comparator.comparing((Product p) -> p.getContract().getFrom()).thenComparing(Product::getFrom))
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
    @Path("/indexValue")
    public Response updateIndexValue(UpdateIndexValueRequest request) throws Exception {
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

        if (!(product instanceof WithIndexValue)) {
            throw new IllegalArgumentException("Contract does not support index value");
        }

        // create a new one
        Product d = product.getClass().getConstructor().newInstance();
        d.setContractId(product.getContractId());
        d.setFrom(request.getFrom());
        d.setTo(product.getTo());
        d.setDiscount(product.getDiscount());
        d.setInsuredSum(product.getInsuredSum());
        ((WithIndexValue) d).setIndexValue(request.getNewIndexValue());
        em.persist(d);
        System.out.println("inserted new product instance version from " + d.getFrom() + " to " + d.getTo() + " with index value " + request.getNewIndexValue());

        // update validity of original
        product.setTo(request.getFrom().minus(1, ChronoUnit.MILLIS));
        System.out.println("updated original with new to date: " + product.getTo());

        // update the rest
        int updateCount = em.createQuery("update Product p " +
                                            "set p.indexValue = :indexValue " +
                                            "where p.contractId = :cid " +
                                            "and p.from >= :ts")
                .setParameter("indexValue", request.getNewIndexValue())
                .setParameter("cid", c.getId())
                .setParameter("ts", d.getTo())
                .executeUpdate();
        System.out.println("updated " + updateCount + " product instance versions with new index value " + request.getNewIndexValue());

        return Response.noContent().build();
    }
}