package ch.maxant.kdc.cip;

import org.keycloak.adapters.authorization.ClaimInformationPointProvider;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.adapters.spi.HttpFacade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class AntClaimInformationPointProvider implements ClaimInformationPointProvider {

    private final Map<String, Object> config;

    public AntClaimInformationPointProvider(Map<String, Object> config, PolicyEnforcer policyEnforcer) {
        this.config = config;
    }

    @Override
    public Map<String, List<String>> resolve(HttpFacade httpFacade) {
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("asdf2", asList("fdas2", "fdas22"));
        return claims;
    }
}
