package ch.maxant.kdc.mf.contracts.definitions

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.util.*

/*
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Car.class, name = "car"),
    @Type(value = Truck.class, name = "truck")
})
 */
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

class Eggs(number: Int) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.QUANTITY, number, Units.PIECES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.EGGS)
        ), emptyList())

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
