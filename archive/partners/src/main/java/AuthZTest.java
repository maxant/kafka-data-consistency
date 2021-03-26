import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class AuthZTest {
    public static void main(String[] args) throws FileNotFoundException {
        //System.setProperty("javax.net.debug", "all");
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "TRACE"); // DEBUG
//        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "TRACE"); // ERROR

        AuthzClient authzClient = AuthzClient.create(); //new FileInputStream("./keycloak.json"));
//authzClient.protection("test", "test").policy("abac").findById("").getDecisionStrategy().
        AuthorizationResource authorization = authzClient.authorization("test", "test");
        AuthorizationRequest req = new AuthorizationRequest();
        req.addPermission("abac");
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("asdf", asList("fdas", "fdas2"));
        req.setClaims(claims);
        AuthorizationResponse authorize = authorization.authorize(req);
        System.out.printf("%s", authorize.getToken());
    }

}
