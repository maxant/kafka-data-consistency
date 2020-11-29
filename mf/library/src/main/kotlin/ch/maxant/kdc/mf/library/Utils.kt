package ch.maxant.kdc.mf.library

import org.jboss.logging.Logger
import javax.validation.ValidationException
import javax.ws.rs.core.Response

private val logger = Logger.getLogger("ValidationExceptionHandler")

fun doByHandlingValidationExceptions(fn: () -> Response): Response {
    return try {
        fn()
    } catch (e: MfValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        logger.info("failing with ${e.javaClass.name}: ${e.message}")
        Response.status(400).entity(Output(e.javaClass.name, e.message, e.data)).build()
    } catch (e: ValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        logger.info("failing with ${e.javaClass.name}: ${e.message}")
        Response.status(400).entity(Output(e.javaClass.name, e.message)).build()
    } catch (e: IllegalArgumentException) {
        // comes out of kotlin's require
        logger.info("failing with ${e.javaClass.name}: ${e.message}")
        Response.status(400).entity(Output(e.javaClass.name, e.message)).build()
    }
}

class Output(val `class`: String, val error: String?, val data: Any? = null)
abstract class MfValidationException(val data: Any): ValidationException()