package ch.maxant.kdc.mf.contracts.definitions

/** like a marker interface to show that this is packaging */
abstract class Packaging(configs: List<Configuration<*>>,
                         children: List<ComponentDefinition>,
                         configPossibilities: List<Configuration<*>> = emptyList()
) : ComponentDefinition(configs, children, configPossibilities)

class CardboardBox(space: CardboardBoxSize, quantity: Int, contents: Product) : Packaging(
        listOf(
                IntConfiguration(ConfigurableParameter.SPACES, space.size, Units.NONE),
                IntConfiguration(ConfigurableParameter.QUANTITY, quantity, Units.NONE),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.CARDBOARD)
        ),
        listOf(contents),
        listOf(
            IntConfiguration(ConfigurableParameter.QUANTITY, 5, Units.NONE),
            IntConfiguration(ConfigurableParameter.QUANTITY, 10, Units.NONE)
        )
) {
    init {
        require(space.size >= quantity)
    }

    enum class CardboardBoxSize(val size: Int) {
        TEN(10)
    }
}

class Pallet(space: PalletSize, quantity: Int, contents: Packaging) : Packaging(
        listOf(
                IntConfiguration(ConfigurableParameter.SPACES, space.size, Units.NONE),
                IntConfiguration(ConfigurableParameter.QUANTITY, quantity, Units.PIECES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.WOOD)
        ), listOf(contents)) {
    init {
        require(space.size >= quantity)
    }

    enum class PalletSize(val size: Int) {
        ONE_HUNDRED(100)
    }
}

object Packagings {
    fun pack(quantity: Int, product: Product): Packaging {
        val maxOrderSize = Pallet.PalletSize.ONE_HUNDRED.size * CardboardBox.CardboardBoxSize.TEN.size
        return if(quantity <= CardboardBox.CardboardBoxSize.TEN.size) {
            CardboardBox(CardboardBox.CardboardBoxSize.TEN, quantity, product)
        } else if(quantity <= maxOrderSize) {
            TODO()
            /*
            val numPallets = BigDecimal(quantity).divide(BigDecimal(CardboardBox.CardboardBoxSize.TEN.size)).round(MathContext(0, RoundingMode.UP)).intValueExact()
            val boxSize = CardboardBox.CardboardBoxSize.TEN
            val box = CardboardBox (boxSize, boxSize.size, product) } )
            Pallet(Pallet.PalletSize.ONE_HUNDRED, numBoxes, box)
             */
        } else {
            throw OrderTooLargeException(quantity, maxOrderSize)
        }
    }

    fun find(componentDefinitionIds: List<String>): Packaging {
        val cardboardBox = CardboardBox(CardboardBox.CardboardBoxSize.TEN, 10, object: Product(ProductId.TEST_PRODUCT, emptyList(), emptyList()){})
        if(componentDefinitionIds.contains(Pallet::class.java.simpleName)) {
            return Pallet(Pallet.PalletSize.ONE_HUNDRED, 100, cardboardBox)
        } else if(componentDefinitionIds.contains(CardboardBox::class.java.simpleName)) {
            return cardboardBox
        } else {
            TODO()
        }
    }
}

class OrderTooLargeException(val quantity: Int, val maxOrderSize: Int) : RuntimeException()
