package ch.maxant.kdc.cip;

import org.keycloak.adapters.authorization.ClaimInformationPointProviderFactory;
import org.keycloak.adapters.authorization.PolicyEnforcer;

import java.util.Map;

public class AntClaimInformationPointProviderFactory implements ClaimInformationPointProviderFactory<AntClaimInformationPointProvider> {

    private PolicyEnforcer policyEnforcer;

    @Override
    public String getName() {
        return "ant";
    }

    @Override
    public void init(PolicyEnforcer policyEnforcer) {
        this.policyEnforcer = policyEnforcer;
    }

    @Override
    public AntClaimInformationPointProvider create(Map<String, Object> config) {
        return new AntClaimInformationPointProvider(config, policyEnforcer);
    }
}