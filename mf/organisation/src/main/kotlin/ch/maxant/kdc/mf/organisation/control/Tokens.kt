package ch.maxant.kdc.mf.organisation.control

import io.smallrye.jwt.auth.cdi.JWTCallerPrincipalFactoryProducer
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo
import io.smallrye.jwt.auth.principal.JWTParser
import io.smallrye.jwt.build.Jwt
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.enterprise.context.Dependent
import javax.inject.Inject


@Dependent
class Tokens {

    @Inject
    lateinit var parser: JWTParser

    @ConfigProperty(name = "ch.maxant.kdc.mf.jwt.secret", defaultValue = "CtjA9hYPuet4Uv3p69T42JUJ6VagkEegkTVWHZxTAqH3dkhchwLVqW6CJeVE8PWbypWD7pkhr57x4RPdDxFy52sNErS9pqJGLEDtT9H74aNvAHr69VG5kRnkMnLhsaFK")
    lateinit var secret: String

    fun generate(staff: Staff): String {
        val now = LocalDateTime.now()
        return Jwt.issuer("https://maxant.ch/issuer")
                .upn("${staff.un}")
                .subject(staff.un)
                .groups(staff.staffRoles.map { it.toString() }.toMutableSet())
                .expiresAt(now.plusMinutes(1).toInstant(ZoneOffset.UTC))
                .issuedAt(now.toInstant(ZoneOffset.UTC))
                .claim("userType", "partner")
                .claim(Claims.email.toString(), staff.getEmail())
                .signWithSecret(secret)
    }

    fun generate(partner: Partner): String {
        val now = LocalDateTime.now()
        return Jwt.issuer("https://maxant.ch/issuer")
                .upn("${partner.un}")
                .subject(partner.un)
                .groups(partner.partnerRoles.map { it.toString() }.toMutableSet())
                .expiresAt(now.plusMinutes(1).toInstant(ZoneOffset.UTC))
                .issuedAt(now.toInstant(ZoneOffset.UTC))
                .claim("userType", "partner")
                .claim("partnerId", partner.partnerId)
                .signWithSecret(secret)
    }

    fun parse(token: String): JsonWebToken {
        return parser.verify(token, secret)
    }
}
