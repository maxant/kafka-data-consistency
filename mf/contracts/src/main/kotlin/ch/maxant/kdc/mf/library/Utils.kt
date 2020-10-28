package ch.maxant.kdc.mf.library

import javax.validation.ValidationException
import javax.ws.rs.core.Response

fun doByHandlingValidationExceptions(fn: () -> Response): Response =
    try {
        fn()
    } catch (e: ValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        Response.status(400).entity("""{"class": "${e.javaClass}", "error": "${e.message}"}""").build()
    }

