package ch.maxant.kdc.claims;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Vector;

@ApplicationScoped
public class ClaimRepository {

    //TODO WRITE THE CLAIM TO DB
    List<Claim> claims = new Vector<>();

    public List<Claim> getClaims() {
        return claims;
    }

    public void createClaim(Claim claim) {
        claims.add(claim);
    }

    public void delete() {
        claims.clear();
    }

}
