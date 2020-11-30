package ch.maxant.kdc.mf.library

import org.jboss.logging.Logger
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

/**
 * allows for RBAC and ABAC. RBAC is checked by checking that the interface is mapped
 * to the process step and that the user has a role that is enabled for the process step.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SecurityCheck(val attributeChecks: Array<AttributeChecks> = [])

class SecurityCheckImpl(abac: AttributeChecks, impl: (ctx: InvocationContext)->Unit)

enum class AttributeChecks {
    CUSTOMER_OWNS_CONTRACT,
    OU_OWNS_CONTRACT,
    USER_IN_HEAD_OFFICE
}

@SecurityCheck
@Interceptor
@SuppressWarnings("unused")
class SecurityCheckInterceptor() {

    var log: Logger = Logger.getLogger(this.javaClass)

    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {
        return ctx.proceed()
    }
}

interface Role {
    fun getDescription(): String
}
