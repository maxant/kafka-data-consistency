package ch.maxant.kdc.mf.contracts.definitions

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.math.BigDecimal
import java.util.*

// TODO only works like this at the mo.
// see https://stackoverflow.com/questions/64812745/jackson-not-generating-subtype-information-when-object-is-not-in-a-list
@JsonSerialize(using = ComponentSerializer::class)
// TODO @JsonDeserialize(using = ComponentDeserializer::class)
abstract class ComponentDefinition(
        val configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>,
        val configPossibilities: List<Configuration<*>> = emptyList() // allows the component to specify possible values deviating from the initial values
) {
    val componentDefinitionId: String = this.javaClass.simpleName
    var componentId: UUID? = null

    init {
        configs.forEach { ensureConfigValueIsPermitted(it) }
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
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("3.5")),
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("6.0"))
        )
    )

class Flour(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.FLOUR)
        ), emptyList())

class Sugar(quantityGr: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.WEIGHT, quantityGr, Units.GRAMS),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.SUGAR)
        ), emptyList())

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

/** @return the definition configured with the given configs */
fun getDefinition(componentDefinitionId: String, configs: ArrayList<Configuration<*>>): ComponentDefinition =
        if(componentDefinitionId == Milk::class.java.simpleName) {
            Milk(
                    configs.find { it.name == ConfigurableParameter.VOLUME }!!.value as Int,
                    configs.find { it.name == ConfigurableParameter.FAT_CONTENT }!!.value as BigDecimal
            )
        } else {
            TODO()
        }
