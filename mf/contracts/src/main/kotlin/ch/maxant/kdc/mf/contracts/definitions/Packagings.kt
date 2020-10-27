package ch.maxant.kdc.mf.contracts.definitions

import org.apache.commons.lang3.Validate
import org.apache.commons.lang3.Validate.isTrue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** like a marker interface to show that this is packaging */
abstract class Packaging(configs: List<Configuration<*>>,
                         children: List<ComponentDefinition>
) : ComponentDefinition(configs, children)

class CardboardBox(space: CardboardBoxSize, quantity: Int, contents: Product) : Packaging(
        listOf(
                IntConfiguration(ConfigurableParameter.SPACES, space.size, Units.NONE),
                IntConfiguration(ConfigurableParameter.QUANTITY, quantity, Units.PIECES),
                MaterialConfiguration(ConfigurableParameter.MATERIAL, Material.CARDBOARD)
        ), listOf(contents)) {
    init {
        Validate.isTrue(space.size >= quantity)
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
        isTrue(space.size >= quantity)
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
}

class OrderTooLargeException(val quantity: Int, val maxOrderSize: Int) : RuntimeException()
