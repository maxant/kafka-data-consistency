package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.telemetry.ComponentName;

import javax.enterprise.inject.Produces;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/rest")
public class RestApplication extends Application {

    @Produces
    @ComponentName
    public String getComponentName() {
        return "claims";
    }
}