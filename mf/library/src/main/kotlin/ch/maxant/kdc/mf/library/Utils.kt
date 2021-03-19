package ch.maxant.kdc.mf.library

import org.jboss.logging.Logger
import javax.transaction.TransactionManager
import javax.validation.ValidationException
import javax.ws.rs.core.Response

private val logger = Logger.getLogger("ValidationExceptionHandler")

fun doByHandlingValidationExceptions(tm: TransactionManager?, fn: () -> Response): Response {
    return try {
        fn()
    } catch (e: MfValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        tm?.setRollbackOnly()
        logger.info("rolling back and failing with ${e.javaClass.name}: ${e.message} at ${e.stackTrace[0].fileName}:${e.stackTrace[0].lineNumber}")
        Response.status(Response.Status.BAD_REQUEST).entity(Output(e.javaClass.name, e.message, e.data)).build()
    } catch (e: ValidationException) {
        // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
        // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
        tm?.setRollbackOnly()
        logger.info("rolling back and failing with ${e.javaClass.name}: ${e.message} at ${e.stackTrace[0].fileName}:${e.stackTrace[0].lineNumber}")
        Response.status(Response.Status.BAD_REQUEST).entity(Output(e.javaClass.name, e.message)).build()
    } catch (e: IllegalArgumentException) {
        // comes out of kotlin's require
        tm?.setRollbackOnly()
        logger.info("rolling back and failing with ${e.javaClass.name}: ${e.message} at ${e.stackTrace[0].fileName}:${e.stackTrace[0].lineNumber}")
        Response.status(Response.Status.BAD_REQUEST).entity(Output(e.javaClass.name, e.message)).build()
    }
}

class Output(val `class`: String, val error: String?, val data: Any? = null)
abstract class MfValidationException(val data: Any): ValidationException()