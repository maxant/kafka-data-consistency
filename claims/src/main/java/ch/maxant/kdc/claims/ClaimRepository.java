package ch.maxant.kdc.claims;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Vector;

@ApplicationScoped
public class ClaimRepository {

    //TODO WRITE THE CLAIM TO ORIENTDB
    List<Claim> claims = new Vector<>();

    public List<Claim> getClaims() {
        return claims;
    }

    public void createClaim(Claim claim) {
        claims.add(claim);
        System.out.println("TODO WRITE THE CLAIM TO ORIENTDB: " + claim.getId());
    }
}
