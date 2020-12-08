package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import io.smallrye.jwt.auth.principal.JWTParser
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi.ProcessAnnotatedType
import javax.enterprise.inject.spi.WithAnnotations
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext
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
annotation class Secure()

private val log: Logger = Logger.getLogger(Secure::class.java)

private lateinit var securityModel: SecurityDefinitionResponse

@Secure
@Interceptor
@SuppressWarnings("unused")
class SecurityCheckInterceptor() {

    @Context
    lateinit var headers :HttpHeaders

    @Inject
    lateinit var parser: JWTParser

    @ConfigProperty(name = "ch.maxant.kdc.mf.jwt.secret", defaultValue = TokenSecret)
    lateinit var secret: String

    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {

        //ctx.method.getAnnotation(Sec::class.java).sendToDlt

        val fqMethodName = "${ctx.method.declaringClass.name}#${ctx.method.name}"
        log.info("checking security for call to $fqMethodName")

        val roles = mutableListOf<String>()
        securityModel.root.forEach { findProcessSteps(fqMethodName, it, roles) }
        log.info("method $fqMethodName can be executed with roles '${roles.joinToString()}'")

        val authTokens = headers.getRequestHeader(SecurityHeaderName)
        if(authTokens.isEmpty()) {
            throw NotAuthorizedException("missing header $SecurityHeaderName")
        } else if(authTokens.size > 1) {
            throw NotAuthorizedException("more than one header $SecurityHeaderName")
        }

        val jwt = parser.verify(authTokens[0], secret)

        if(jwt.issuer != Issuer) throw NotAuthorizedException("wrong issuer ${jwt.issuer}")
        if(jwt.expirationTime < System.currentTimeMillis()) throw NotAuthorizedException("token expired at ${jwt.expirationTime}")
        if(roles.intersect(jwt.groups).isEmpty()) throw NotAuthorizedException("token does not contain one of the required roles $roles necessary to call $fqMethodName")

        return ctx.proceed()
    }

}

private fun findProcessSteps(fqMethodName: String, node: Node, roles: MutableList<String>) {
    if(node.data.methods.contains(fqMethodName) && node.data.roleMappings != null) {
        roles.addAll(node.data.roleMappings.split(","))
    }
    node.children.forEach { findProcessSteps(fqMethodName, it, roles) }
}

@ApplicationScoped
class SecurityEnsurer {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var securityAdapter: SecurityAdapter

    fun init(@Observes e: StartupEvent) {
        // TODO fail hard if we cannot get details!
        log.info("fetching security model")
        securityModel = securityAdapter.getSecurityConfiguration()
        log.info("loaded security model")

        // TODO check methods that are annotated also have a definition
        //securityModel.root.forEach { findProcessSteps(fqMethodName, it, roles) }
    }

    fun <T> typeFound(@Observes @WithAnnotations(Secure::class) event: ProcessAnnotatedType<T>) {
        println("HERE")
    }
}

interface Role {
    fun getDescription(): String
}
