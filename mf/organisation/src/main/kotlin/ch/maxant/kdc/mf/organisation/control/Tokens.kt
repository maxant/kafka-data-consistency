package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.library.Issuer
import ch.maxant.kdc.mf.library.TokenSecret
import io.smallrye.jwt.auth.principal.JWTParser
import io.smallrye.jwt.build.Jwt
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger
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
                .expiresAt(now.plusMinutes(1).atZone(ZoneId.systemDefault()).toInstant())
                .issuedAt(now.toInstant(ZoneOffset.UTC))
        if(user is Partner) {
            builder
                .claim("userType", "partner")
                .claim("partnerId", user.partnerId)
        } else if(user is Staff) {
            builder
                .claim("userType", "staff")
                .claim(Claims.email.toString(), user.getEmail())
        } else throw TODO()
        return builder.signWithSecret(secret)
    }

    fun parseAndVerify(token: String): JsonWebToken {
        return parser.verify(token, secret)
    }
}
