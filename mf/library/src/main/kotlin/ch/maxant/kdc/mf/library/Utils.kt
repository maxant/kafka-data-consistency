package ch.maxant.kdc.mf.library

import javax.validation.ValidationException
import javax.ws.rs.core.Response

// TODO add logging to this, otherwise you dont see the problem in the service provider logs
fun doByHandlingValidationExceptions(fn: () -> Response): Response =
    try {
        fn()
    } catch (e: ValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        Response.status(400).entity("""{"class": "${e.javaClass}", "error": "${e.message}"}""").build()
    } catch (e: IllegalArgumentException) {
        // comes out of kotlin's require
        Response.status(400).entity("""{"class": "${e.javaClass}", "error": "${e.message}"}""").build()
    }

