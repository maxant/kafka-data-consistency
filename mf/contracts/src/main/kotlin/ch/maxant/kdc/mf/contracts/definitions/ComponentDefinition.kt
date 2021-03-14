package ch.maxant.kdc.mf.contracts.definitions

import org.mvel2.MVEL
import java.math.BigDecimal

abstract class AbstractComponentDefinition(
        var configs: List<Configuration<*>>,
        var configPossibilities: List<Configuration<*>> = emptyList(), // allows the component to specify possible values deviating from the initial values

        // how often can this component be added to it's parent?
        // this is on the wrong side of the relationship and belongs in the parent, so that a parent can define
        // e.g. how much sugar is in a recipe, but i cant be bothered to refactor the entire model to add a wrapper
        // around each child to contain this data (this is a poc after all).
        // cardinalityDefault is the value used during instantiation.
        var cardinalityMin: Int = 1,
        var cardinalityMax: Int = 1,
        var cardinalityDefault: Int = 1
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

    fun runRules(configs: List<Configuration<*>>, component: Any? = this) {
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
        cardinalityMin: Int = 1,
        cardinalityMax: Int = 1,
        cardinalityDefault: Int = 1
): AbstractComponentDefinition(configs, configPossibilities, cardinalityMin, cardinalityMax, cardinalityDefault) {
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

class Sugar(cardinalityMin: Int, cardinalityMax: Int, cardinalityDefault: Int, quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.SUGAR)
        ), emptyList(), emptyList(), cardinalityMin, cardinalityMax, cardinalityDefault)

/** there is just one of these in the parent. if you want more sugar, adjust the weight.
 * if you want more flavour, change the cardinality of the kids */
class VanillaSugar : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, 5, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.SUGAR)
        ), listOf(VanillaExtract()),
        listOf(
            IntConfiguration(ConfigurableParameter.WEIGHT, 0, Units.GRAMS),
            IntConfiguration(ConfigurableParameter.WEIGHT, 5, Units.GRAMS),
            IntConfiguration(ConfigurableParameter.WEIGHT, 10, Units.GRAMS),
            IntConfiguration(ConfigurableParameter.WEIGHT, 15, Units.GRAMS)
        ))

class VanillaExtract : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, 5, Units.MILLILITRES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.VANILLA)
        ), emptyList(), emptyList(), cardinalityMax = 5)

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
            Sugar(0, 3, 1, quantityGr / 3),
            Flour(quantityGr / 3)
        )
)

class GlassBottle(volumeMl: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, volumeMl, Units.MILLILITRES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.GLASS)
        ), emptyList())
