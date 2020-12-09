package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.StartupEvent
import io.smallrye.jwt.auth.principal.JWTParser
import org.apache.commons.lang3.StringUtils
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi.ProcessAnnotatedType
import javax.enterprise.inject.spi.WithAnnotations
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders

const val SecurityHeaderName = "mfauthorization"

/** used to sign and verify tokens. TODO replace with PKI so that the secret doesnt need to be deployed in all images */
const val TokenSecret = "CtjA9hYPuet4Uv3p69T42JUJ6VagkEegkTVWHZxTAqH3dkhchwLVqW6CJeVE8PWbypWD7pkhr57x4RPdDxFy52sNErS9pqJGLEDtT9H74aNvAHr69VG5kRnkMnLhsaFK"

const val Issuer = "https://maxant.ch/issuer"

/**
 * allows for RBAC. RBAC is checked by checking that the interface is mapped
 * to the process step and that the user has a role that is enabled for the process step.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Secure

private val log: Logger = Logger.getLogger(Secure::class.java)

private var securityModel: SecurityDefinitionResponse? = null

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

        val fqMethodName = "${ctx.method.declaringClass.name}#${ctx.method.name}"
        log.info("checking security for call to $fqMethodName")

        val roles = mutableListOf<String>()
        getSecurityModel().root.forEach { findProcessSteps(fqMethodName, it, roles) }
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
        if(securityModel == null) {
            // can happen if organisation service is not available at startup
            // and its startup message has not yet arrived, but a rest call to
            // this service has, so fetch it now
            securityModel = securityAdapter.getSecurityConfiguration()
        }
        return securityModel!!
    }

}

private fun findProcessSteps(fqMethodName: String, node: Node, roles: MutableList<String>) {
    if(node.data.methods.contains(fqMethodName) && node.data.roleMappings != null) {
        roles.addAll(node.data.roleMappings.split(","))
    }
    node.children.forEach { findProcessSteps(fqMethodName, it, roles) }
}

@ApplicationScoped
@SuppressWarnings("unused")
class SecurityEnsurer {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var securityAdapter: SecurityAdapter

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var context: ch.maxant.kdc.mf.library.Context

    @Incoming("organisation-in")
    @PimpedAndWithDltAndAck
    fun process(msg: Message<String>): CompletionStage<*> {
        try {
            when (context.event) {
                "SECURITY_MODEL" -> {
                    log.info("received new security model")
                    securityModel = om.readValue(msg.payload)
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
            log.info("fetching security model")
            securityModel = securityAdapter.getSecurityConfiguration()
            log.info("loaded security model")
        } catch(e: Exception) {
            log.error("failed to get security model at startup. deferring to first rest call that needs " +
                    "it, or an event from the organisation application when/if it reboots", e)
        }

        // TODO check methods that are annotated also have a definition, otherwise warn devops
        //securityModel.root.forEach { findProcessSteps(fqMethodName, it, roles) }
    }

    fun <T> typeFound(@Observes @WithAnnotations(Secure::class) event: ProcessAnnotatedType<T>) {
        println("HERE") // TODO this doesnt work with quarkus :-( ?
    }

    // TODO read from offset minus 1
}

interface Role {
    fun getDescription(): String
}
