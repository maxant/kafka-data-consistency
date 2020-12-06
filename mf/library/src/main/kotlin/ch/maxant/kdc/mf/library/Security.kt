package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

const val SecurityHeaderName = "mfauthorization"

/**
 * allows for RBAC. RBAC is checked by checking that the interface is mapped
 * to the process step and that the user has a role that is enabled for the process step.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Secure()

private val log: Logger = Logger.getLogger(Secure::class.java)

@Secure
@Interceptor
@SuppressWarnings("unused")
class SecurityCheckInterceptor() {


    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {
        log.info("checking security for call to ${ctx.target}#${ctx.method.name}")
        return ctx.proceed()
    }
}

private lateinit var model: SecurityModel

class SecurityModel {

}

@ApplicationScoped
class SecurityEnsurer {
    fun init(@Observes e: StartupEvent) {
        log.info("fetching security model")
    }
}

interface Role {
    fun getDescription(): String
}
