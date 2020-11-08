package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal
import java.util.*

abstract class ComponentDefinition(
        val configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>
) {
    val componentDefinitionId: String = this.javaClass.simpleName
    var componentId: UUID? = null

    /** @return a list containing this element and all its children, recursively */
    fun getThisAndAllChildren(list: MutableList<ComponentDefinition> = mutableListOf()): List<ComponentDefinition> {
        list.add(this)
        children.forEach { it.getThisAndAllChildren(list) }
        return list
    }
}

class Milk(quantityMl: Int, fatContentPercent: BigDecimal) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.VOLUME, quantityMl, Units.MILLILITRES),
                PercentConfiguration(ConfigurableParameter.FAT_CONTENT, fatContentPercent),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.MILK)
        ), emptyList())

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

