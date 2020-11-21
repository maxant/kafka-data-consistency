package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.kafka.common.header.internals.RecordHeaders
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata
import org.jboss.logging.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.concurrent.CompletionStage
import javax.interceptor.InvocationContext

class PimpedAndWithDltAndAckTest {

    private lateinit var log: Logger
    private lateinit var errorHandler: ErrorHandler
    private lateinit var ic: InvocationContext

    @BeforeEach
    fun setup() {
        log = mock{}
        errorHandler = mock{}
    }

    @AfterEach
    fun teardown() {
        verifyNoMoreInteractions(ic, log, errorHandler)
    }

    @Test
    fun annotationAddedToMethodWithWrongNumParams() {
        ic = mock {
            on { parameters } doReturn(arrayOf())
            on { method } doReturn(TestConsumerWrongNumParams::class.java.getMethod("process")) // <--- WRONG NUM PARAMS
            on { target } doReturn(TestConsumerWrongNumParams())
        }

        // when + then
        assertEquals("EH001 @ErrorHandled on method ch.maxant.kdc.mf.library.TestConsumerWrongNumParams::process must contain exactly one parameter",
                assertThrows<IllegalArgumentException> { PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper()).invoke(ic) }.message)
        verify(ic).parameters
        verify(ic).target
        verify(ic).method
    }

    @Test
    fun annotationAddedToMethodWithWrongParamType() {
        ic = mock {
            on { parameters } doReturn(arrayOf(1))
            on { method } doReturn(TestConsumerWrongParamType::class.java.getMethod("process", Int::class.java)) // <--- WRONG PARAM TYPE
            on { target } doReturn(TestConsumerWrongParamType())
        }

        // when + then
        assertEquals("EH002 @ErrorHandled on method ch.maxant.kdc.mf.library.TestConsumerWrongParamType::process must contain one String/Message parameter",
                assertThrows<IllegalArgumentException> { PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper()).invoke(ic) }.message)
        verify(ic, times(2)).parameters
        verify(ic).target
        verify(ic).method
    }

    @Test
    fun invoke_happy_string_not_json() {
        ic = mock {
            on { parameters } doReturn(arrayOf("asdf")) // <--- NOT JSON
            on { method } doReturn(TestStringConsumerSendToDlt::class.java.getMethod("process", String::class.java))
            on { target } doReturn(TestStringConsumerSendToDlt())
        }
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when + then
        assertEquals("""Unrecognized token 'asdf': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
 at [Source: (String)"asdf"; line: 1, column: 5]""",
                assertThrows<JsonParseException> { sut.invoke(ic) }.message)

        verify(ic, times(2)).parameters
        verify(ic, never()).proceed()
    }

    @Test
    fun invoke_happy_string() {
        ic = mock {
            on { parameters } doReturn(arrayOf("{}"))
            on { method } doReturn(TestStringConsumerSendToDlt::class.java.getMethod("process", String::class.java))
            on { target } doReturn(TestStringConsumerSendToDlt())
        }
        whenever(ic.proceed()).thenReturn(null)
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.invoke(ic)

        // then
        assertNull(ret)

        verify(ic, times(2)).parameters
        verify(ic).proceed()
        verify(log).debug("processing incoming message")
    }

    @Test
    fun invoke_message_happy() {
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders().add(REQUEST_ID, "123".toByteArray()))
        }
        val msg = Message.of("{}").addMetadata(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerSendToDlt::class.java.getMethod("process", Message::class.java))
            on { target } doReturn(TestMessageConsumerSendToDlt())
        }
        whenever(ic.proceed()).thenReturn(completedFuture(null))
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.invoke(ic)

        // then
        assertNull((ret as CompletableFuture<*>).get())

        verify(ic, times(2)).parameters
        verify(ic).proceed()
        verify(log).debug("processing incoming message")
        verify(log).debug("kafka acked message")
    }

    @Test
    fun invoke_message_exceptionInProceed() {
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders().add(REQUEST_ID, "1".toByteArray()))
        }
        val msg = MockMessage(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerSendToDlt::class.java.getMethod("process", Message::class.java))
            on { target } doReturn(TestMessageConsumerSendToDlt())
        }
        val e = RuntimeException("test")
        whenever(ic.proceed()).thenThrow(e) // <--- STEERS TEST
        whenever(errorHandler.dlt(msg, e)).thenReturn(completedFuture(null))
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.invoke(ic)

        // then
        assertNull((ret as CompletableFuture<*>).get())
        assertEquals(1, msg.ackCount)

        verify(ic, times(3)).parameters
        verify(ic).method
        verify(ic).proceed()
        verify(log).warn("""EH004 failed to process message {} - sending it to the DLT with requestId 1""", e)
        verify(log).debug("processing incoming message")
        verify(log).debug("kafka acked message")
        verify(errorHandler).dlt(msg, e)
    }

    @Test
    fun invoke_message_exceptionInProceedResult() { // ie in the completion stage returned from downstream
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders().add(REQUEST_ID, "1".toByteArray()))
        }
        val msg = MockMessage(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerSendToDlt::class.java.getMethod("process", Message::class.java))
            on { target } doReturn(TestMessageConsumerSendToDlt())
        }
        val e = RuntimeException("test")
        whenever(ic.proceed()).thenReturn(failedFuture<Unit>(e)) // <--- STEERS TEST
        whenever(errorHandler.dlt(msg, e)).thenReturn(completedFuture(null))
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.invoke(ic)

        // then
        assertNull((ret as CompletableFuture<*>).get())
        assertEquals(1, msg.ackCount)

        verify(ic, times(3)).parameters
        verify(ic).method
        verify(ic).proceed()
        verify(log).warn("""EH004 failed to process message {} - sending it to the DLT with requestId 1""", e)
        verify(log).debug("processing incoming message")
        verify(log).debug("kafka acked message")
        verify(errorHandler).dlt(msg, e)
    }

    @Test
    fun dealWithExceptionIfNecessary_noException() {
        ic = mock {}
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())

        // when
        val ret = sut.dealWithExceptionIfNecessary(null, ic, Context())

        // then
        assertNull((ret as CompletableFuture<*>).get())
    }

    @Test
    fun dealWithExceptionIfNecessary_dltFails() {
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders().add(REQUEST_ID, "1".toByteArray()))
        }
        val msg = MockMessage(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerSendToDlt::class.java.getMethod("process", Message::class.java))
            on { target } doReturn(TestMessageConsumerSendToDlt())
        }
        val originalException = RuntimeException("original")
        val dltException = RuntimeException("test")
        whenever(errorHandler.dlt(msg, originalException)).thenThrow(dltException) // <--- STEERS TEST
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.dealWithExceptionIfNecessary(originalException, ic, Context.of(RequestId("1"), msg))

        // then
        assertNull((ret as CompletableFuture<*>).get())
        assertEquals(0, msg.ackCount) // ack is called outside of method being tested

        verify(ic).parameters
        verify(ic).method
        verify(log).warn("""EH004 failed to process message {} - sending it to the DLT with requestId 1""", originalException)
        verify(log).error("EH006a failed to process message {} - this message is being dumped here", dltException)
        verify(log).error("EH006b original exception was", originalException)
        verify(errorHandler).dlt(msg, originalException)
    }

    @Test
    fun dealWithExceptionIfNecessary_notToDlt() {
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders().add(REQUEST_ID, "1".toByteArray()))
        }
        val msg = MockMessage(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerDontSendToDlt::class.java.getMethod("process", Message::class.java)) // <--- STEERS TEST
            on { target } doReturn(TestMessageConsumerDontSendToDlt())
        }
        val originalException = RuntimeException("original")
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.dealWithExceptionIfNecessary(originalException, ic, Context.of(RequestId("1"), msg))

        // then
        assertNull((ret as CompletableFuture<*>).get())
        assertEquals(0, msg.ackCount) // ack is called outside of method being tested

        verify(ic).parameters
        verify(ic).method
        verify(log).error("""EH005 failed to process message {} - NOT sending it to the DLT because @PimpedWithDlt is configured with 'sendToDlt = false' - this message is being dumped here""", originalException)
        verify(errorHandler, never()).dlt(any(), any())
    }

    @Test
    fun dealWithExceptionIfNecessary_missingRequestId() {
        val metadata = mock<IncomingKafkaRecordMetadata<String, String>> {
            on { headers } doReturn (RecordHeaders()) // <--- STEERS TEST
        }
        val msg = MockMessage(metadata)
        ic = mock {
            on { parameters } doReturn(arrayOf(msg))
            on { method } doReturn(TestMessageConsumerDontSendToDlt::class.java.getMethod("process", Message::class.java))
            on { target } doReturn(TestMessageConsumerDontSendToDlt())
        }
        val originalException = RuntimeException("original")
        val sut = PimpedAndWithDltAndAckInterceptor(errorHandler, Context(), ObjectMapper())
        sut.log = log

        // when
        val ret = sut.dealWithExceptionIfNecessary(originalException, ic, Context.of(RequestId(""), msg)) // <--- STEERS TEST

        // then
        assertNull((ret as CompletableFuture<*>).get())
        assertEquals(0, msg.ackCount) // ack is called outside of method being tested

        verify(ic).parameters
        verify(log).error("""EH003 failed to process message {} - unknown requestId so not sending it to the DLT - this message is being dumped here - this is an error in the program - every message MUST have a requestId header or attribute at the root""", originalException)
        verify(errorHandler, never()).dlt(any(), any())
    }
}

class TestStringConsumerSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck
    fun process(s: String) = Unit
}

class TestStringConsumerDontSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck(false)
    fun process(s: String) = Unit
}

class TestMessageConsumerSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck
    fun process(m: Message<String>) = Unit
}

class TestMessageConsumerDontSendToDlt {
    @SuppressWarnings("unused")
    @PimpedAndWithDltAndAck(false)
    fun process(m: Message<String>) = Unit
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

class MockMessage(kMetadata: IncomingKafkaRecordMetadata<String, String>) : Message<String> {
    var ackCount = 0
    val md: Metadata

    init{
        md = super.getMetadata().with(kMetadata)
    }

    override fun getPayload(): String = "{}"

    override fun ack(): CompletionStage<Void> {
        ackCount++
        return completedFuture(null)
    }

    override fun getMetadata(): Metadata = md
}