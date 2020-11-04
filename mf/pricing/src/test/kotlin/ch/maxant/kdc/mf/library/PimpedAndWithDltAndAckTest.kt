package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import org.jboss.logging.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.interceptor.InvocationContext

class PimpedAndWithDltAndAckTest {

    lateinit var log: Logger
    lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setup() {
        log = mock{}
        errorHandler = mock{}
    }

    @AfterEach
    fun teardown() {
        verifyNoMoreInteractions(log, errorHandler)
    }

    @Test
    fun notJson() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn (arrayOf("asdf")) // <--- NOT JSON
            on { method } doReturn (TestConsumerSendToDlt::class.java.getMethod("process", String::class.java))
        }
        val ei = PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper())
        ei.log = log

        // when
        ei.dealWithException(Exception(), ic)

        // then
        verify(log).error(eq("EH006a failed to process message asdf - this message is being dumped here"), any<JsonParseException>())
        verify(log).error(eq("EH006b original exception was"), any<RuntimeException>())
    }

    @Test
    fun noRequestId() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn(arrayOf("{}")) // <--- NO requestId
            on { method } doReturn(TestConsumerSendToDlt::class.java.getMethod("process", String::class.java))
        }

        // when
        val ei = PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper())
        ei.log = log

        // when
        ei.dealWithException(Exception(), ic)

        // then
        verify(log).error(eq("EH003 failed to process message {} " +
                "- unknown requestId so not sending it to the DLT " +
                "- this message is being dumped here " +
                "- this is an error in the program " +
                "- every message MUST have a requestId attribute at the root"), any<Exception>())
    }

    @Test
    fun requestIdSentToDlt() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn(arrayOf("""{ "requestId": "1" }""")) // <--- WITH requestId
            on { method } doReturn(TestConsumerSendToDlt::class.java.getMethod("process", String::class.java)) // <--- SEND TO DLT
        }
        val e = Exception()
        val ei = PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper())
        ei.log = log

        // when
        ei.dealWithException(e, ic)

        // then
        verify(log).warn(eq("""EH004 failed to process message { "requestId": "1" } - sending it to the DLT with requestId "1""""), any<Exception>())
        verify(errorHandler).dlt(eq("1"), anyOrNull(), eq(e), eq("""{ "requestId": "1" }"""))
    }

    @Test
    fun requestIdDontSentToDlt() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn(arrayOf("""{ "requestId": "1" }""")) // <--- WITH requestId
            on { method } doReturn(TestConsumerDontSendToDlt::class.java.getMethod("process", String::class.java)) // <--- DONT SEND TO DLT
        }
        val ei = PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper())
        ei.log = log

        // when
        ei.dealWithException(Exception(), ic)

        // then
        verify(log).error(eq("EH005 failed to process message { \"requestId\": \"1\" } " +
                "- NOT sending it to the DLT because @PimpedWithDlt is configured with 'sendToDlt = false' " +
                "- this message is being dumped here"), any<Exception>())
    }

    @Test
    fun annotationAddedToMethodWithWrongNumParams() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn(arrayOf())
            on { method } doReturn(TestConsumerWrongNumParams::class.java.getMethod("process")) // <--- WRONG NUM PARAMS
            on { target } doReturn(TestConsumerWrongNumParams::class.java)
        }

        // when + then
        assertEquals("EH001 @ErrorHandled on method java.lang.Class::process must contain exactly one parameter",
                assertThrows<IllegalArgumentException> { PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper()).invoke(ic) }.message)
    }

    @Test
    fun annotationAddedToMethodWithWrongParamType() {
        val ic = mock<InvocationContext> {
            on { parameters } doReturn(arrayOf(1))
            on { method } doReturn(TestConsumerWrongParamType::class.java.getMethod("process", Int::class.java)) // <--- WRONG PARAM TYPE
            on { target } doReturn(TestConsumerWrongParamType::class.java)
        }

        // when + then
        assertEquals("EH002 @ErrorHandled on method java.lang.Class::process must contain one string parameter",
                assertThrows<IllegalArgumentException> { PimpedAndWithDltAndAckInterceptor(errorHandler, ObjectMapper()).invoke(ic) }.message)
    }
}

class TestConsumerSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck()
    fun process(s: String) = Unit
}

class TestConsumerDontSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck(false)
    fun process(s: String) = Unit
}

class TestConsumerWrongNumParams {
    @PimpedAndWithDltAndAck(false)
    fun process() = Unit
}

class TestConsumerWrongParamType {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck(false)
    fun process(i: Int) = Unit
}
