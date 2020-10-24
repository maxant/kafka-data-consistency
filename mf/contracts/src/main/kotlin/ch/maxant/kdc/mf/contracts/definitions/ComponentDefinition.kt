package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal

abstract class ComponentDefinition(
        val configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>
) {
    val className = this.javaClass.simpleName
}

/** like a marker interface to show that this is packaging */
abstract class Packaging(configs: List<Configuration<*>>,
                       children: List<ComponentDefinition>
) : ComponentDefinition(configs, children)

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

class CardboardBox(space: CardboardBoxSize, quantity: Int, contents: Product) : Packaging(
        listOf(
                IntConfiguration(ConfigurableParameter.SPACES, space.size, Units.NONE),
                IntConfiguration(ConfigurableParameter.QUANTITY, quantity, Units.PIECES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.CARDBOARD)
        ), listOf(contents)) {
    init {
        assert(space.size >= quantity)
    }

    enum class CardboardBoxSize(val size: Int) {
        TEN(10)
    }
}

class Pallet(space: PalletSize, quantity: Int, contents: Packaging) : ComponentDefinition(
        listOf(
                IntConfiguration(ConfigurableParameter.SPACES, space.size, Units.NONE),
                IntConfiguration(ConfigurableParameter.QUANTITY, quantity, Units.PIECES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.WOOD)
        ), listOf(contents)) {
    init {
        assert(space.size >= quantity)
    }

    enum class PalletSize(val size: Int) {
        ONE_HUNDRED(100)
    }
}
