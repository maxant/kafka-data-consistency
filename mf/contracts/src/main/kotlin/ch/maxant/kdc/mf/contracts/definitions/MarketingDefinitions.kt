package ch.maxant.kdc.mf.contracts.definitions

/** overrides config from component definition */
data class DefaultConfiguration(
    val name: ConfigurableParameter,
    val value: String
)

/** overrides cardinality and config from component definition */
data class DefaultComponent(
    val path: Regex,
    val configs: List<DefaultConfiguration>,
    val configPossibilities: List<DefaultConfiguration>,
    val cardinalityMin: Int = 1,
    val cardinalityMax: Int = 1
) {
    constructor(path: String, configs: List<DefaultConfiguration>, configPossibilities: List<DefaultConfiguration>, cardinalityMin: Int, cardinalityMax: Int):
            this(Regex(path), configs, configPossibilities, cardinalityMin, cardinalityMax)
}

/**
 * By default, we use the Product definitions, but we can override them with marketing definitions based
 * on the profile and product.
 */
class MarketingDefinitions(private val defaultComponents: List<DefaultComponent>) {

    fun getComponent(path: String) = defaultComponents.find { path.matches(it.path) }

    companion object {

        private fun coffeLatteSkinnyDefaults(profile: Profile) =
            if(profile.interestedInStrongFlavours) {
                listOf(
                    DefaultComponent("Drink->VanillaSugar->VanillaExtract\\d+", emptyList(), emptyList(), 2, 6)
                )
            } else emptyList()

        private fun getProductIdFromTree(componentDefinition: ComponentDefinition): ProductId? {
            if(componentDefinition is Product) return componentDefinition.productId
            for(child in componentDefinition.children) {
                return getProductIdFromTree(child)
            }
            return null
        }

        fun getDefaults(profile: Profile, productId: ProductId) = MarketingDefinitions(when(productId) {
            ProductId.COFFEE_LATTE_SKINNY -> coffeLatteSkinnyDefaults(profile)
            else -> emptyList() // no defaults
        })
    }
}