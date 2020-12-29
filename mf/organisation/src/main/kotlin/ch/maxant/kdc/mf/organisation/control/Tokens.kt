package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.library.Issuer
import ch.maxant.kdc.mf.library.TokenSecret
import io.smallrye.jwt.auth.principal.JWTParser
import io.smallrye.jwt.build.Jwt
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject


@Dependent
class Tokens {

    @Inject
    lateinit var parser: JWTParser

    @ConfigProperty(name = "ch.maxant.kdc.mf.jwt.secret", defaultValue = TokenSecret)
    lateinit var secret: String

    fun generate(user: User): String {
        val now = LocalDateTime.now()
        val builder = Jwt.issuer(Issuer)
                .upn("${user.un}")
                .subject(user.un)
                .groups(user.roles.map { it.toString() }.toMutableSet())
                .expiresAt(toInstant(now.plusMinutes(15)))
                .issuedAt(toInstant(now))
                .claim("partnerId", user.partnerId)
        if(user is Partner) {
            builder
                .claim("userType", "partner")
        } else if(user is Staff) {
            builder
                .claim("userType", "staff")
                .claim(Claims.email.toString(), user.getEmail())
        } else throw TODO()
        return builder.signWithSecret(secret)
    }

    private fun toInstant(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant()

    fun parseAndVerify(token: String): JsonWebToken {
        return parser.verify(token, secret)
    }
}
