package ch.maxant.kdc.claims;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateClaimHandler {

    public void createClaim(Claim claim) {
        System.out.println("TODO WRITE THE CLAIM TO ORIENTDB: " + claim.getId());
    }
}
