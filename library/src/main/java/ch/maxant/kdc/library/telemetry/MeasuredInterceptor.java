package ch.maxant.kdc.library.telemetry;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.io.Serializable;

@Measured
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class MeasuredInterceptor implements Serializable {

    @Inject
    TelemetryService telemetryService;

    @AroundInvoke
    public Object measure(InvocationContext ctx) throws Exception {
        try {
            long start = System.currentTimeMillis();
            Object o = ctx.proceed();
            telemetryService.log(start);
            return o;
        } catch (Exception e) {
            return null;
        }
    }
}