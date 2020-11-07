package ch.maxant

import ch.maxant.kdc.mf.library.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

class CompletionStageExperiments {

    @Test
    fun combine() {
        // we're doing something async, and when it completes, we want to join with a second CS, returning a new
        // CS which is only completed once the first two are
        val ack: () -> CompletableFuture<String> = { completedFuture("a") }
        val cf = completedFuture("b")
        val cf2 = cf.thenCombine(ack()) { a, b -> a + b }
        assertEquals("ba", cf2.get())
    }

    @Test
    fun thenAccept() {
        val cf = completedFuture("b")
        val cf2 = cf.thenAccept { println("$it"); "a$it" }
        assertNull(cf2.get()) // accept throws the result away and results in a null CS, but gives you access to the previous result
    }

    @Test
    fun thenRun() {
        val cf = completedFuture("b")
        val cf2 = cf.thenRun { println("a") }
        assertNull(cf2.get()) // simply runs code at the end and results in a null CS
    }

    @Test
    fun thenCompose() {
        val ack: () -> CompletionStage<String> = { completedFuture("b") }
        val cf1 = completedFuture("a")
        val cf2 = cf1.thenCompose { println("$it"); ack() }
        assertEquals("b", cf2.get()) // allows you to return a second CS which needs completing, so like compose, without the merging function => equivalent to flatMap
    }

    @Test
    fun thenApply() {
        val cf = completedFuture("b")
        val cf2 = cf.thenApply { println(it); "a" }
        assertEquals("a", cf2.get()) // allows you to map from a value to another
    }

    @Test
    fun thenAcceptBoth() {
        val cf1 = completedFuture("a")
        val cf2 = completedFuture("b")
        val cf3 = cf1.thenAcceptBoth(cf2) { a, b -> println("$a$b") }
        assertNull(cf3.get()) // only runs if both are successful, but results in a null, like thenAccept!
    }

    @Test
    fun handleWithAck_wrong() {
        val ack: () -> CompletableFuture<String> = { completedFuture("a") }
        val cf = completedFuture("b")
        val cf2 = cf.handle { _, _ ->
            ack().get() // doing this makes no sense => we want to combine, not handle! since that avoids the call to get
        }
        assertEquals("a", cf2.get())
    }

    @Test
    fun handle() {
        val cf = completedFuture("b")
        val cf2 = cf.handle { r, _ ->
            "c$r"
        }
        assertEquals("cb", cf2.get()) // handle does a transformation
    }

    @Test
    fun whenComplete() {
        val cf = completedFuture("b")
        val cf2 = cf.whenComplete { r, _ ->
            println("c$r")
        }
        assertEquals("b", cf2.get()) // whenComplete does no transformation. it's also equivalent to a finally block
    }

    @Test
    fun exceptionally() {
        var cf = failedFuture<String>(Exception("a"))
        var cf2 = cf.exceptionally { _ -> "b" }
        assertEquals("b", cf2.get()) // only executed when there's a failure, and allows you to transform the exception

        cf = completedFuture("a")
        cf2 = cf.exceptionally { _ -> "b" }
        assertEquals("a", cf2.get()) // as this time its successul, no transformation occurs
    }

    @Test
    fun joinVsGet() {

        // join doesnt throw checked exceptions => kinda irrelevant for kotlin

        class MyException(msg: String): Exception(msg)
        var cf = failedFuture<String>(MyException("b"))
        assertEquals("b", assertThrows<CompletionException> { cf.join() }.cause?.message)

        cf = failedFuture<String>(MyException("b"))
        assertEquals("b", assertThrows<ExecutionException> { cf.get() }.cause?.message )
    }

    @Test
    fun nested_aka_flatMap() {
        // https://www.baeldung.com/java-completablefuture => search for nested => equivalent to flatMap
        val computeAnother: (i: Int) -> CompletableFuture<Int> = { completedFuture(it) }
        val finalResult = completedFuture(3).thenCompose(computeAnother)
        assertEquals(3, finalResult.join())
    }

    @Test
    fun nestedUsingPair_toGetComposeExceptionally() {
        val ctx = Context()
        fun dealWithException(t: Throwable, ctx: Context): CompletableFuture<String> {
            return completedFuture("dealt with exception")
        }

        fun dwe(p: Pair<String, Throwable>): CompletionStage<String> {
            if(p.second != null) {
                return dealWithException(p.second, ctx)
            } else {
                return completedFuture("no exception found")
            }
        }

        var cf = completedFuture("a")
                .handle { s, t -> Pair(s, t)} // put them together so that we can decide downstream how to deal with the exception if there is one
                .thenCompose { p -> dwe(p)}

        assertEquals("no exception found", cf.join())

        cf = failedFuture<String>(Exception("a"))
                .handle { s, t -> Pair(s, t)} // ditto
                .thenCompose { p -> dwe(p)}

        assertEquals("dealt with exception", cf.join())
    }

    @Test
    fun nestedUsingX() {
        val cf: CompletableFuture<String> = completedFuture("a")

        val cf2: CompletableFuture<CompletableFuture<String>> = completedFuture("b")
                .thenApply { cf } // cf is nested and results in CS<CS<*>>

        val cf3: CompletableFuture<String> = cf2
                .thenCompose { it } // use compose(it) in order to get it flattened

        assertEquals("a", cf3.get())
    }

}

