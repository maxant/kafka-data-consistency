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
class Milkshake(productId: ProductId, quantityMl: Int, recipe: (Int)-> List<ComponentDefinition>) : Product(
        productId,
        listOf(IntConfiguration(ConfigurableParameter.VOLUME, quantityMl, Units.MILLILITRES)),
        recipe(quantityMl)
)

enum class ProductId {
    COOKIES_MILKSHAKE,

    /**
     * used for testing only as it contains every possible complexity so that we can test them all live.
     * isn't valid in prod
     */
    TEST_PRODUCT
}

class UnknownProductException(productId: ProductId) : RuntimeException(productId.toString())

object Products {

    private fun cookiesMilkshake(quantityMl: Int) = Milkshake(ProductId.COOKIES_MILKSHAKE, quantityMl) { qty ->
        listOf(
                Milk(95 * qty / 100, BigDecimal("1.8")),
                Cookies(45 * (qty / 1000)),
                GlassBottle(qty)
        )
    }

    fun find(productId: ProductId, quantityMl: Int): Product {
        return when(productId) {
            ProductId.COOKIES_MILKSHAKE -> cookiesMilkshake(quantityMl)
            else -> throw UnknownProductException(productId)
        }
    }

}