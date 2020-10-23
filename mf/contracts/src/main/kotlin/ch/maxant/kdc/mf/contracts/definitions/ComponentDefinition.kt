package ch.maxant.kdc.mf.contracts.definitions

import ch.maxant.kdc.mf.contracts.entity.ProductId
import java.lang.RuntimeException
import java.math.BigDecimal

class ComponentDefinition(
        val configs: List<Configuration<*>>,
        val children: List<ComponentDefinition>
) {

    companion object {

        fun grams(material: Material, grams: Int): ComponentDefinition =
            ComponentDefinition(
                    listOf(
                            MaterialConfiguration(
                                    IntConfiguration(ConfigurableParameter.WEIGHT_IN_GRAMS, grams),
                                    material)),
                    emptyList())

        fun quantity(material: Material, quantity: Int): ComponentDefinition =
            ComponentDefinition(
                    listOf(
                            MaterialConfiguration(
                                    IntConfiguration(ConfigurableParameter.QUANTITY, quantity),
                                    material)),
                    emptyList())

        val oneHundredGramsSugar = grams(Material.SUGAR, 100)
        val threeEggs = quantity(Material.EGGS, 3)
        val cookies = ComponentDefinition(emptyList(), listOf(oneHundredGramsSugar, threeEggs))

        val glassBottle = StringConfiguration(ConfigurableParameter.MATERIAL, "Glass")
        val fatContentSixPercent = PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("6.00"))
        val tenGrams = BigDecimalConfiguration(ConfigurableParameter.WEIGHT, BigDecimal("10"))
        val sugar = ComponentDefinition(listOf(tenGrams), emptyList())
        val chocolateCookies = ComponentDefinition(listOf(tenGrams), listOf(sugar, eggs))
        val bottle = ComponentDefinition(listOf(glassBottle), emptyList())
        val cookiesMilkshake = ComponentDefinition(emptyList(), listOf(
                ComponentDefinition(listOf(fatContentSixPercent), emptyList()), // milk

                milk, bottle, chocolateCookies))

        private val definitions: List<ComponentDefinition> = listOf(
                cookiesMilkshake
        )

        fun find(productId: ProductId) =
                definitions.stream()
                        .filter {
                                it.productId.equals(productId)
                        }.findFirst()
                        .orElseThrow { RuntimeException("No matching contract definition - try a different date") }
    }
}