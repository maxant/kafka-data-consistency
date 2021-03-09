package ch.maxant.kdc.mf.contracts.definitions

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.mvel2.MVEL
import java.math.BigDecimal
import java.util.*

// TODO only works like this at the mo.
// see https://stackoverflow.com/questions/64812745/jackson-not-generating-subtype-information-when-object-is-not-in-a-list
@JsonSerialize(using = ComponentSerializer::class)
// TODO @JsonDeserialize(using = ComponentDeserializer::class)
abstract class ComponentDefinition(
        val configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>,
        var configPossibilities: List<Configuration<*>> = emptyList(), // allows the component to specify possible values deviating from the initial values

        // how often can this component be added to it's parent?
        // this is probably on the wrong side of the relationship and belongs in the parent, but i cant be bothered to
        // refactor the entire model to add a wrapper around each child to contain this data (this is a poc after all),
        // and sticking it in eg a
        // map is too hacky
        var cardinalityMin: Int = 1,
        var cardinalityMax: Int = 1
) {
    val componentDefinitionId: String = this.javaClass.simpleName
    var componentId: UUID? = null
    var rules: List<String> = emptyList()

    init {
        configs.forEach {
            ensureConfigValueIsPermitted(it)
        }
        runRules(configs)
    }

    /** @return a list containing this element and all its children, recursively */
    fun getThisAndAllChildren(list: MutableList<ComponentDefinition> = mutableListOf()): List<ComponentDefinition> {
        list.add(this)
        children.forEach { it.getThisAndAllChildren(list) }
        return list
    }

    fun ensureConfigValueIsPermitted(config: Configuration<*>) {
        val possibleConfigs = configPossibilities.filter { it.name == config.name }
        if(possibleConfigs.isNotEmpty()) {
            // TODO add support for PercentRangeConfiguration
            val valueIsPermitted = possibleConfigs.any { it.value == config.value }
            require(valueIsPermitted) { "Component configuration value ${config.value} is not in the permitted set of values ${possibleConfigs.map { it.value }}" }
        } // else anything goes
    }

    fun runRules(configs: List<Configuration<*>>) {
        val contextMap = configs.map { it.name.toString() to it.value }.toMap().toMutableMap()
        contextMap["component"] = this // allows rule to access kids for example
        rules.forEach {
            require(MVEL.evalToBoolean(it, contextMap)) { "Rule evaluated to false: $it with context $contextMap" }
        }
    }
}

class ComponentSerializer: StdSerializer<ComponentDefinition>(ComponentDefinition::class.java) {
    override fun serialize(value: ComponentDefinition, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
            gen.writeStringField("c**", value.javaClass.simpleName)
            gen.writeObjectField("configs", value.configs)
            gen.writeObjectField("children", value.children)
            gen.writeObjectField("configPossibilities", value.configPossibilities)
            gen.writeStringField("componentDefinitionId", value.componentDefinitionId)
            if(value.componentId == null) {
                gen.writeNullField("componentId")
            } else {
                gen.writeStringField("componentId", value.componentId.toString())
            }
            if(value.rules == null) {
                gen.writeNullField("rules")
            } else {
                gen.writeArrayFieldStart("rules")
                value.rules.forEach { gen.writeString(it) }
                gen.writeEndArray()
            }
            if(value is Product) {
                gen.writeStringField("productId", value.productId.toString())
            }
        gen.writeEndObject()
    }
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

class VanillaExtract() : ComponentDefinition(
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

/**
 * @return the definition matching the given componentDefinitionIdTofind,
 * configured with the given configs,
 * with rules and config possibilities from the actual product.
 *
 * @param possibleComponentDefinitionTrees a list, because you might not have packaging and contents connected!
 */
fun getDefinition(
    possibleComponentDefinitionTrees: List<ComponentDefinition>,
    componentDefinitionIdToFind: String,
    configs: List<Configuration<*>>
): ComponentDefinition {
    var componentDefinition = possibleComponentDefinitionTrees
        .map { findComponentDefinitionRecursively(it, componentDefinitionIdToFind) }
        .find { it != null }
    require(componentDefinition != null) { "no definition found for $componentDefinitionIdToFind" }
    configs.forEach { config ->
        val c = componentDefinition.configs.find { it.name == config.name }!!
        c.setValueExplicit(config.value)
    }
    return componentDefinition
}

private fun findComponentDefinitionRecursively(componentDefinition: ComponentDefinition, componentDefinitionId: String): ComponentDefinition? {
    if(componentDefinition.componentDefinitionId == componentDefinitionId) {
        return componentDefinition
    } else {
        for (child in componentDefinition.children) {
            val defn = findComponentDefinitionRecursively(child, componentDefinitionId)
            if(defn != null) return defn
        }
        return null
    }
}