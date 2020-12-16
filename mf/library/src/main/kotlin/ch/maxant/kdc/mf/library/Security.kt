package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.jwt.auth.principal.JWTParser
import org.apache.commons.lang3.StringUtils
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import org.quartz.*
import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.control.ActivateRequestContext
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.NotAuthorizedException


const val SecurityHeaderName = "mfauthorization"

/** used to sign and verify tokens. TODO replace with PKI so that the secret doesnt need to be deployed in all images */
const val TokenSecret = "CtjA9hYPuet4Uv3p69T42JUJ6VagkEegkTVWHZxTAqH3dkhchwLVqW6CJeVE8PWbypWD7pkhr57x4RPdDxFy52sNErS9pqJGLEDtT9H74aNvAHr69VG5kRnkMnLhsaFK"

const val Issuer = "https://maxant.ch/issuer"

/**
 * allows for RBAC. RBAC is checked by checking that the interface is mapped
 * to the process step and that the user has a role that is enabled for the process step.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Secure

private val log: Logger = Logger.getLogger(Secure::class.java)

private var securityModel: SecurityDefinitionResponse? = null
private var secureMethods = mutableListOf<Method>()

@Secure
@Interceptor
@SuppressWarnings("unused")
class SecurityCheckInterceptor {

    @Inject
    lateinit var request: HttpServletRequest

    @Inject
    lateinit var parser: JWTParser

    @ConfigProperty(name = "ch.maxant.kdc.mf.jwt.secret", defaultValue = TokenSecret)
    lateinit var secret: String

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var securityAdapter: SecurityAdapter

    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {

        val fqMethodName = getFqMethodName(ctx.method)
        log.info("checking security for call to $fqMethodName")

        val roles = mutableListOf<String>()
        getSecurityModel().root.forEach { findRolesThatCanExecuteMethod(fqMethodName, it, roles) }
        log.info("method $fqMethodName can be executed with roles '${roles.joinToString()}'")

        val authToken = request.getHeader(SecurityHeaderName)
        if(StringUtils.isEmpty(authToken)) {
            throw NotAuthorizedException("missing header $SecurityHeaderName")
        }

        val jwt = parser.verify(authToken.substring("Bearer ".length), secret)

        if(jwt.issuer != Issuer) throw NotAuthorizedException("wrong issuer ${jwt.issuer}")
        if((1000*jwt.expirationTime )< System.currentTimeMillis()) throw NotAuthorizedException("token expired at ${jwt.expirationTime}")
        if(roles.intersect(jwt.groups).isEmpty()) throw NotAuthorizedException("token does not contain one of the required roles $roles necessary to call $fqMethodName")

        log.info("user ${jwt.subject} is entitled to call $fqMethodName")

        return ctx.proceed()
    }

    private fun getSecurityModel(): SecurityDefinitionResponse {
        synchronized(lock) {
            if(securityModel == null) {
                // can happen if organisation service is not available at startup
                // and its startup message has not yet arrived, but a rest call to
                // this service has, so fetch it now
                loadSecurityModel(securityAdapter)
            }
            return securityModel!!
        }
    }

}

private fun findRolesThatCanExecuteMethod(fqMethodName: String, node: Node, roles: MutableList<String>) {
    if(node.data.methods.contains(fqMethodName) && node.data.roleMappings != null) {
        roles.addAll(node.data.roleMappings.split(","))
    }
    node.children.forEach { findRolesThatCanExecuteMethod(fqMethodName, it, roles) }
}

private fun loadSecurityModel(securityAdapter: SecurityAdapter) {
    if(ConfigProvider.getConfig().getValue("quarkus.application.name", String::class.java) == "organisation") {
        log.info("not loading security model via REST, as this is the organisation component which will publish the " +
                "security model using kafka and that will arrive very shortly, and right now, this service isn't " +
                "ready to receive REST requests, as it's still starting up.")
    } else {
        log.info("loading security model")
        setSecurityModelAndDoSecurityChecks(securityAdapter.getSecurityConfiguration())
        log.info("loaded security model")
    }
}

private fun getFqMethodName(method: Method) = "${method.declaringClass.name}#${method.name}"

@ApplicationScoped
@SuppressWarnings("unused")
class SecurityEnsurer {

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var securityAdapter: SecurityAdapter

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var scheduler: Scheduler

    // TODO read from offset minus 1 - then we dont need to use rest, altho we need a suitable retention time
    @Incoming("organisation-in")
    @PimpedAndWithDltAndAck
    fun process(msg: Message<String>): CompletionStage<*> {
        try {
            when (context.event) {
                "SECURITY_MODEL" -> {
                    log.info("received new security model")
                    synchronized(lock) {
                        setSecurityModelAndDoSecurityChecks(om.readValue(msg.payload))
                    }
                }
                // else -> ignore other messages
            }
        } catch (e: Exception) {
            log.warn("failed to process security model ${msg.payload}", e)
        }
        return CompletableFuture.completedFuture(Unit)
    }

    fun init(@Observes e: ContextInitialised) {
        try {
            loadSecurityModel(securityAdapter)
        } catch(e: Exception) {
            log.warn("SEC002 failed to get security model at startup. trying again in 1 second. ${e.message}")

            val job = JobBuilder.newJob(LoadSecurityModelJob::class.java).build()
            val trigger = TriggerBuilder.newTrigger()
                    .startNow()
                    //.withSchedule(simpleSchedule().withRepeatCount(0))
                    .build()
            scheduler.scheduleJob(job, trigger)
        }
    }
}

private val lock = Object()

/** check methods that are annotated also have a definition, otherwise warn devops */
private fun setSecurityModelAndDoSecurityChecks(sm: SecurityDefinitionResponse) {
    securityModel = sm
    val start = System.currentTimeMillis()
    if(secureMethods.isEmpty()) {
        val refls = Reflections("ch.maxant.kdc.mf", MethodAnnotationsScanner())
        secureMethods.addAll(refls.getMethodsAnnotatedWith(Secure::class.java))
    }
    log.info("found the following secure methods:\r\n" +
            secureMethods.joinToString("\r\n", transform = ::getFqMethodName))
    for(method in secureMethods) {
        val fqMethodName = getFqMethodName(method)
        val roles = mutableListOf<String>()
        securityModel?.root?.forEach { findRolesThatCanExecuteMethod(fqMethodName, it, roles) }
        if(roles.isEmpty()) log.warn("SEC001 no process steps / roles found in security model that are " +
                "capable of calling $fqMethodName. If this method is called, it will always end in a security " +
                "exception. To resolve this issue, update the security model or delete the method, if it is no longer " +
                "required.")
    }
    log.info("security checks completed in ${(System.currentTimeMillis() - start)}ms")
}

@ActivateRequestContext
class LoadSecurityModelJob: Job {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var securityAdapter: SecurityAdapter

    override fun execute(context: JobExecutionContext?) {
        synchronized(lock) {
            if(securityModel == null) {
                log.info("trying to reload security model")
                try {
                    loadSecurityModel(securityAdapter)
                } catch(e: Exception) {
                    log.warn("SEC003 failed to get security model after startup. deferring to first rest call that needs " +
                            "it, or an event from the organisation application when/if it reboots", e)
                }
            } else log.info("security model already loaded")
        }
    }
}

interface Role {
    fun getDescription(): String
}
