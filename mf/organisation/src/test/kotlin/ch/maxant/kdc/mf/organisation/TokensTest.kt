package ch.maxant.kdc.mf.organisation

import ch.maxant.kdc.mf.organisation.control.Staff
import ch.maxant.kdc.mf.organisation.control.Tokens
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.inject.Inject

@QuarkusTest
class TokensTest {
    @Inject
    lateinit var tokens: Tokens

    @Test
    fun test() {
        val token = tokens.generate(Staff.JOHN)
        println(token)
        val jwt = tokens.parse(token)
        assertEquals(setOf("SALES_REP", "FINANCE_SPECIALIST"), jwt.groups)
        assertEquals("john.smith@mf.maxant.ch", jwt.claim<String>("email").get())
    }
}