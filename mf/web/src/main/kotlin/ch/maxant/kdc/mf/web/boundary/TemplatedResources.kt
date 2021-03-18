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

/** adds vue, primevue and prime icons */
private fun TemplateInstance.addStandardLibraries() =
        // primevue version (pvversion): search for version numbers in npm: https://www.npmjs.com/package/primevue or use "" for latest? not sure that acutally works properly, coz had issues where suddenly a version 2 was used
        // vue version (vueversion): search for version numbers in npm, or use "next"
        // also checkout the footer of this: https://primefaces.org/primevue/showcase/#/theming,
        // it says: PrimeVue 3.1.1 on Vue 3.0.3 by PrimeTek
        this.data("pvversion", "@3.1.1")
            .data("vueversion", "@3.0.4")
            .data("vuerouterversion", "@4.0.2")
            .data("primeiconsversion", "@4.1.0")
            .data("primeflexversion", "@2.0.0")
            .data("rxjsversion", "@6.6.3")

/** adds the given primevue widgets */
private fun TemplateInstance.addPvWidgets(pvcomponents: List<String>) =
        this.data("pvcomponents", pvcomponents)

/** adds the given milk factory widgets */
private fun TemplateInstance.addMfWidgets(components: List<Component>, additionalComponents: List<String> = listOf()) =
        this.data("mfwidgets",
                listOf("users.js")
                    + components.map { "${it.uiWidgetsJavascript}" }
                    + additionalComponents.map { "$it.js" }
        )

/** adds all the constants for the various microservice components in the landscape */
private fun TemplateInstance.addMfComponents() =
        this.data("mfcomponents", Component.values())

@ApplicationScoped
@Path("/portal")
@Produces(MediaType.TEXT_HTML)
class PortalTemplate {

    @Inject
    lateinit var portal: Template

    @GET
    fun get(): TemplateInstance =
        portal.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("calendar", "dropdown", "autocomplete", "tabview", "tabpanel", "inputtext", "button"))
            .addMfWidgets(listOf(Component.Contracts, Component.Partners),
                    listOf("vue-rxjs", "portal-home", "portal-sales",
                            "portal-order", "portal-order-details",
                            "portal-contract-details", "portal-demo"))
            .addMfComponents()
}

@ApplicationScoped
@Path("/partner")
@Produces(MediaType.TEXT_HTML)
class PartnerTemplate {

    @Inject
    lateinit var partner: Template

    @GET
    fun get(): TemplateInstance =
            partner.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("calendar", "dropdown", "autocomplete", "inputtext", "button"))
            .addMfWidgets(listOf(Component.Cases, Component.Contracts, Component.Partners))
            .addMfComponents()
}

@ApplicationScoped
@Path("/contract")
@Produces(MediaType.TEXT_HTML)
class ContractTemplate {

    @Inject
    lateinit var contract: Template

    @GET
    fun get(): TemplateInstance =
            contract.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("calendar", "dropdown", "autocomplete", "inputtext", "button", "treetable", "column"))
            .addMfWidgets(listOf(Component.Cases, Component.Contracts, Component.Partners))
            .addMfComponents()
}

@ApplicationScoped
@Path("/sales")
@Produces(MediaType.TEXT_HTML)
class SalesTemplate {

    @Inject
    lateinit var sales: Template

    @GET
    fun get(): TemplateInstance =
            sales.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("calendar", "dropdown", "autocomplete", "inputtext", "button", "inputnumber", "progressbar", "tooltip", "checkbox"))
            .addMfWidgets(listOf(Component.Contracts, Component.Partners))
            .addMfComponents()
}

@ApplicationScoped
@Path("/search")
@Produces(MediaType.TEXT_HTML)
class SearchTemplate {

    @Inject
    lateinit var search: Template

    @GET
    fun get(): TemplateInstance =
            search.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("calendar", "dropdown", "autocomplete", "inputtext", "button"))
            .addMfWidgets(listOf(Component.Contracts, Component.Partners),
                listOf("vue-rxjs")
            )
            .addMfComponents()
}

@ApplicationScoped
@Path("/organisation")
@Produces(MediaType.TEXT_HTML)
class OrganisationTemplate {

    @Inject
    lateinit var organisation: Template

    @GET
    fun get(): TemplateInstance =
            organisation.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("dropdown", "autocomplete", "treetable", "column"))
            .addMfWidgets(listOf())
            .addMfComponents()
}

@ApplicationScoped
@Path("/security")
@Produces(MediaType.TEXT_HTML)
class SecurityTemplate {

    @Inject
    lateinit var security: Template

    @GET
    fun get(): TemplateInstance =
            security.instance()
            .addStandardLibraries()
            .addPvWidgets(listOf("dropdown", "autocomplete", "treetable", "column"))
            .addMfWidgets(listOf())
            .addMfComponents()
}

enum class Component(val constantName: String, val uiWidgetsJavascript: String, val baseUrl: String) {
    Contracts     ("CONTRACTS",     "contracts.js",    ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.contracts.url",     String::class.java).orElse("http://contracts:8080")),
    Pricing       ("PRICING",       "pricing.js",      ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.pricing.url",       String::class.java).orElse("http://pricing:8081")),
    Web           ("WEB",           "web.js",          ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.web.url",           String::class.java).orElse("http://web:8082")),
    Partners      ("PARTNERS",      "partners.js",     ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.partners.url",      String::class.java).orElse("http://partners:8083")),
    Cases         ("CASES",         "cases.js",        ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.cases.url",         String::class.java).orElse("http://cases:8084")),
    Waitingroom   ("WAITINGROOM",   "waitingroom.js",  ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.waitingroom.url",   String::class.java).orElse("http://waitingroom:8085")),
    Organisation  ("ORGANISATION",  "organisation.js", ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.organisation.url",  String::class.java).orElse("http://organisation:8086")),
    Billing       ("BILLING",       "billing.js",      ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.billing.url",       String::class.java).orElse("http://billing:8087")),
    Dsc           ("DSC",           "dsc.js",          ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.dsc.url",           String::class.java).orElse("http://dsc:8088")),
    ElasticSearch ("ELASTICSEARCH", "elastic.js",      ConfigProvider.getConfig().getOptionalValue("ch.maxant.kdc.mf.components.elasticsearch.url", String::class.java).orElse("http://kdc.elasticsearch.maxant.ch")),
}
