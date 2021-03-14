package ch.maxant.kdc.mf.contracts.definitions

import org.mvel2.MVEL
import java.math.BigDecimal

abstract class AbstractComponentDefinition(
        var configs: List<Configuration<*>>,
        var configPossibilities: List<Configuration<*>> = emptyList(), // allows the component to specify possible values deviating from the initial values

        // how often can this component be added to it's parent?
        // this is probably on the wrong side of the relationship and belongs in the parent, but i cant be bothered to
        // refactor the entire model to add a wrapper around each child to contain this data (this is a poc after all),
        // and sticking it in eg a
        // map is too hacky
        var cardinalityMin: Int = 1,
        var cardinalityMax: Int = 1
) {
    var rules: List<String> = emptyList()

    init {
        configs.forEach {
            ensureConfigValueIsPermitted(it)
        }
        // dont run rules here, because if they refer to other parts of the tree,
        // and the tree isnt complete yet, they will fail unnecessarily
    }

    fun ensureConfigValueIsPermitted(config: Configuration<*>) {
        val possibleConfigs = configPossibilities.filter { it.name == config.name }
        if(possibleConfigs.isNotEmpty()) {
            // TODO add support for PercentRangeConfiguration
            val valueIsPermitted = possibleConfigs.any { it.value == config.value }
            require(valueIsPermitted) { "Component configuration value ${config.value} is not in the permitted set of values ${possibleConfigs.map { it.value }}" }
        } // else anything goes
    }

    fun runRules(configs: List<Configuration<*>>, component: Any?) {
        val contextMap = configs.map { it.name.toString() to it.value }.toMap().toMutableMap()
        contextMap["component"] = component // allows rule to access kids for example
        rules.forEach {
            require(MVEL.evalToBoolean(it, contextMap)) { "Rule evaluated to false: $it with context $contextMap" }
        }
    }
}

abstract class ComponentDefinition(
        configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>,
        configPossibilities: List<Configuration<*>> = emptyList(), // allows the component to specify possible values deviating from the initial values

        // how often can this component be added to it's parent?
        // this is probably on the wrong side of the relationship and belongs in the parent, but i cant be bothered to
        // refactor the entire model to add a wrapper around each child to contain this data (this is a poc after all),
        // and sticking it in eg a
        // map is too hacky
        cardinalityMin: Int = 1,
        cardinalityMax: Int = 1
): AbstractComponentDefinition(configs, configPossibilities, cardinalityMin, cardinalityMax) {
    val componentDefinitionId: String = this.javaClass.simpleName
}

class Milk(quantityMl: Int, fatContentPercent: BigDecimal) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, quantityMl, Units.MILLILITRES),
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, fatContentPercent),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.MILK)
        ),
        emptyList(),
        listOf(
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("0.2")),
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("1.8")),
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("3.5"))
        )
    )

class Flour(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.FLOUR)
        ), emptyList())

class CoffeePowder(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.COFFEE_POWDER)
        ), emptyList())

class Sugar(quantityGr: Int, maxCardinality: Int = 1) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.SUGAR)
        ), emptyList(), emptyList(), 0, maxCardinality)

/** add one of these and adjust the gram quantity to get more sugar. if you want more
 * vanilla, add more children, up to 5! */
class VanillaSugar(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.SUGAR)
        ), listOf(VanillaExtract()), emptyList(), 1, 1)

class VanillaExtract : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, 5, Units.MILLILITRES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.VANILLA)
        ), emptyList(), emptyList(), 1, 5)

class Butter(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.BUTTER)
        ), emptyList())

class Cookies(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS)
        ), listOf(
            Butter(quantityGr / 3),
            Sugar(quantityGr / 3),
            Flour(quantityGr / 3)
        )
)

class GlassBottle(volumeMl: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, volumeMl, Units.MILLILITRES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.GLASS)
        ), emptyList())
