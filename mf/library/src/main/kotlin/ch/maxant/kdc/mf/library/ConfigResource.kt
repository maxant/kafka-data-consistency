package ch.maxant.kdc.mf.library

import org.eclipse.microprofile.config.ConfigProvider
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/config")
@SuppressWarnings("unused")
class ConfigResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun get() = Response.ok(
        ConfigProvider.getConfig().propertyNames.map { name ->
            try {
                "$name -> ${ConfigProvider.getConfig().getValue(name, String::class.java)}"
            } catch (e: NoSuchElementException) {
                "$name -> <no value>"
            } catch (e: Exception) {
                "$name -> error: ${e.message}"
            }
        }).build()
}
