package ch.maxant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KotlinExperiments {

    @Test
    fun functions() {
        // this is an anonymous function:
        val len = fun(s: String): Int { return s.length }
        assertEquals(3, len("len"))

        // this is a lambda:
        val b = { t1: String, t2: String -> t1.length + t2.length }
        assertEquals(2, b("a", "b"))

        // https://dev.to/frevib/kotlin-extension-function-vs-function-literal-with-receiver-411d#:~:text=In%20Kotlin%20it%20is%20possible,a%20function%20literal%20with%20receiver.
        // In Kotlin it is possible to add a method (called member function in Kotlin) to an existing class. This is called an extension function.
        // It is also possible to access a member function from a class inside a function literal. This is called a function literal with receiver.

        // this is an extension function:
        fun String.appendMonkey(postFix: String): String = this.plus("Monkey").plus(postFix)
        assertEquals("aMonkeyEatsBananas", "a".appendMonkey("EatsBananas"))

        // Function literal with receiver:
        // Where with extension functions you can add a new member function to an existing class, with a function literal with
        // receiver you can access the member functions of an existing class inside the lambda block (inside the curly braces {}).
        // the following is a function that can be called on a string and passed an int, and returns a long
        val addOneToLength:String.(Int)->Long = {it + this.length.toLong()} // access to it, and this
        // val addOneToLength:String.(Int)->Long = {it + this.length.toLong()}
        //     | name
        //                     | receiver type
        //                             | inputs
        //                                   | outputs
        //                                         | function body
        assertEquals(5, "fdsa".addOneToLength(1))

        // on the contrary, if we remove "String." from the above, we just define a function which takes an Int and returns a Long. it is the input
        val addTwo:(Int)->Long = {it + 2L}
        assertEquals(3, addTwo(1))
    }

    @Test
    fun dsls() {
        class HTML {
            fun body() {
                println("in body")
            }
        }

        fun html(init: HTML.() -> Unit): HTML {
            val html = HTML()  // create the receiver object
            html.init()        // pass the receiver object to the lambda
            return html
        }

        html {       // lambda with receiver begins here
            body()   // calling a method on the receiver object
        }

    }

    @Test
    fun dsls3() {
        class DatabaseConnection {
            fun begin() {}
            fun commit() {}
            fun rollback() {}
            fun get(i: Int): String = if (i % 2 == 0) "got $i" else throw RuntimeException()
        }
        fun doInTx(conn: DatabaseConnection, fn: (DatabaseConnection) -> String): String {
            conn.begin()
            try {
                val r = fn.invoke(conn)
                conn.commit()
                return r
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException(e)
            }
        }

        val c = DatabaseConnection()
        assertThrows<RuntimeException> { doInTx(c) { it.get(3) } }
        assertEquals("got 2", doInTx(c) { it.get(2) })
    }

}

