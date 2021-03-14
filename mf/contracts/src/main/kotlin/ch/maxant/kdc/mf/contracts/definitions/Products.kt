package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal

/** like a marker interface to show that this is an end product which can be packed */
abstract class Product(
        val productId: ProductId,
        configs: List<Configuration<*>>,
        children: List<ComponentDefinition>
) : ComponentDefinition(configs, children)

/**
 * @param recipe a function taking the total quantity of liquid, and returning all the components used to make the drink
 */
class Drink(productId: ProductId, quantityMl: Int, recipe: ()-> List<ComponentDefinition>) : Product(
        productId,
        listOf(IntConfiguration(ConfigurableParameter.VOLUME, quantityMl, Units.MILLILITRES)),
        recipe()
)

enum class ProductId {
    COOKIES_MILKSHAKE,
    COFFEE_LATTE_SKINNY,

    /**
     * used for testing only as it contains every possible complexity so that we can test them all live.
     * isn't valid in prod
     */
    TEST_PRODUCT
}

class UnknownProductException(productId: ProductId) : RuntimeException(productId.toString())

object Products {

    private fun cookiesMilkshake(quantityMl: Int) = Drink(ProductId.COOKIES_MILKSHAKE, quantityMl) {
        listOf(
                Milk(95 * quantityMl / 100, BigDecimal("1.8")),
                Cookies(45 * (quantityMl / 1000)),
                GlassBottle(quantityMl)
        )
    }

    private fun coffeeLatteSkinny(quantityMl: Int) = Drink(ProductId.COFFEE_LATTE_SKINNY, quantityMl) {
        // filled 990ml of a litre to allow space for other ingredients
        // default fat content 0.2%
        val skinnyMilk = Milk(990 * quantityMl / 1000, BigDecimal("0.2"))
        skinnyMilk.rules = listOf("FAT_CONTENT <= 1.5")
        val newConfigPossibilities = skinnyMilk.configPossibilities.toMutableList()
        newConfigPossibilities.add(PercentConfiguration(ConfigurableParameter.FAT_CONTENT, BigDecimal("1.4")))
        skinnyMilk.configPossibilities = newConfigPossibilities
        listOf(
                skinnyMilk,
                CoffeePowder(10 * (quantityMl / 1000)),
                VanillaSugar(), // user can add multiple vanillas as kids to increase the strength of the flavour
                GlassBottle(quantityMl)
        )
    }

    fun find(productId: ProductId, quantityMl: Int): Product {
        return when(productId) {
            ProductId.COOKIES_MILKSHAKE -> cookiesMilkshake(quantityMl)
            ProductId.COFFEE_LATTE_SKINNY -> coffeeLatteSkinny(quantityMl)
            else -> throw UnknownProductException(productId)
        }
    }

}