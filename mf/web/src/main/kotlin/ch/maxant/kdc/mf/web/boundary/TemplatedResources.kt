package ch.maxant.kdc.mf.web.boundary

import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import org.eclipse.microprofile.config.ConfigProvider
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

private fun TemplateInstance.addStandardLibraries() =
        // also checkout the footer of this: https://primefaces.org/primevue/showcase/#/theming,
        // it says: PrimeVue 3.1.1 on Vue 3.0.3 by PrimeTek
        // TODO upgrade to 3.1.1 and vue3.0.3/4. see https://github.com/primefaces/primevue/issues/808
        this.data("pvversion", "@3.0.2") // search for version numbers in npm: https://www.npmjs.com/package/primevue or use "" for latest? not sure that acutally works properly, coz had issues where suddenly a version 2 was used
            .data("vueversion", "3.0.2") // search for version numbers in npm, or use "next"
            .data("pvcomponents", listOf("calendar", "dropdown"))
            .data("primeiconsversion", "@4.1.0")

private fun TemplateInstance.addMfWidgets(components: List<Components>) =
        this.data("mfwidgets", components.map { "${it.baseUrl}/${it.uiWidgetsJavascript}" })

private fun TemplateInstance.addMfComponents() =
        this.data("mfcomponents", Components.values())

@ApplicationScoped
@Path("/portal")
@Produces(MediaType.TEXT_HTML)
class PortalResource {

    @Inject
    lateinit var portal: Template

    @GET
    fun get(): TemplateInstance =
        portal.instance()
            .addStandardLibraries()
            .addMfWidgets(listOf(Components.Contracts, Components.Partners))
            .addMfComponents()
}

@ApplicationScoped
@Path("/partner")
@Produces(MediaType.TEXT_HTML)
class PartnerResource {

    @Inject
    lateinit var partner: Template

    @GET
    fun get(): TemplateInstance =
            partner.instance()
            .addStandardLibraries()
            .addMfWidgets(listOf(Components.Contracts, Components.Partners))
            .addMfComponents()
}

@ApplicationScoped
@Path("/sales")
@Produces(MediaType.TEXT_HTML)
class SalesResource {

    @Inject
    lateinit var sales: Template

    @GET
    fun get(): TemplateInstance =
            sales.instance()
            .addStandardLibraries()
            .addMfWidgets(listOf(Components.Contracts, Components.Partners))
            .addMfComponents()
}

enum class Components(val constantName: String, val uiWidgetsJavascript: String, val baseUrl: String) {
    Contracts    ("CONTRACTS",    "contracts.js",    ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.contracts.url",    String::class.java).orElse("http://localhost:8080")),
    Pricing      ("PRICING",      "pricing.js",      ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.pricing.url",      String::class.java).orElse("http://localhost:8081")),
    Web          ("WEB",          "web.js",          ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.web.url",          String::class.java).orElse("http://localhost:8082")),
    Partners     ("PARTNERS",     "partners.js",     ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.partners.url",     String::class.java).orElse("http://localhost:8083")),
    Cases        ("CASES",        "cases.js",        ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.cases.url",        String::class.java).orElse("http://localhost:8084")),
    Waitingroom  ("WAITINGROOM",  "waitingroom.js",  ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.waitingroom.url",  String::class.java).orElse("http://localhost:8085")),
    Organisation ("ORGANISATION", "organisation.js", ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.organisation.url", String::class.java).orElse("http://localhost:8086")),
}
