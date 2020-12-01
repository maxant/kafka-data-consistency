package ch.maxant.kdc.mf.web.boundary

import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@ApplicationScoped
@Path("/portal")
@Produces(MediaType.TEXT_HTML)
class PortalResource {

    @Inject
    lateinit var portal: Template

    @GET
    fun item(): TemplateInstance =
        portal.instance()
              .data("mfcomponents", listOf("http://localhost:8083/partner.js"))
              .data("pvversion", "@3.0.2") // search for version numbers in npm: https://www.npmjs.com/package/primevue or use "" for latest? not sure that acutally works properly, coz had issues where suddenly a version 2 was used
              .data("vueversion", "3.0.2") // search for version numbers in npm, or use "next"
              .data("pvcomponents", listOf("calendar", "dropdown"))
              .data("primeiconsversion", "@4.1.0")
}
