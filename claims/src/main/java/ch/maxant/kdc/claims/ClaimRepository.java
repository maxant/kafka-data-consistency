package ch.maxant.kdc.claims;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Vector;

@ApplicationScoped
public class ClaimRepository {

    @PersistenceContext(unitName = "MySql")
    private EntityManager em;

    //TODO WRITE THE CLAIM TO DB
    List<Claim> claims = new Vector<>();

    public List<Claim> getClaims() {
        return claims;
    }

    public void createClaim(Claim claim) {
        claims.add(claim);

        List<Object> results = em.createNativeQuery("show databases").getResultList();
        System.out.println(results);

        System.out.println("TODO WRITE THE CLAIM TO ORIENTDB: " + claim.getId());
    }

    public void delete() {
        claims.clear();
    }

    // just for testing purposes
    public static void main(String[] args) throws Exception {
        //Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:30300/mysql", "root", "secret");
        Connection connection = DriverManager.getConnection("jdbc:mysql://maxant.ch:30300/mysql", "root", "secret");
        PreparedStatement ps = connection.prepareStatement("show databases");
        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            System.out.println(rs.getString(1));
        }
    }

}
