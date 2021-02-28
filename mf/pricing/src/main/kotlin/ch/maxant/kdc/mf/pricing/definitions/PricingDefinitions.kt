package ch.maxant.kdc.mf.pricing.definitions

import ch.maxant.kdc.mf.pricing.dto.TreeComponent
import ch.maxant.kdc.mf.pricing.dto.Configuration
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import javax.validation.ValidationException

private val log: Logger = Logger.getLogger("PricingDefinitions")

private val RANDOM = Random()
private val TAX = BigDecimal(0.077)

data class Price(val total: BigDecimal, val tax: BigDecimal) {
    fun add(p: Price) = Price(this.total.add(p.total), this.tax.add(p.tax))
    fun multiply(value: BigDecimal): Price = Price(this.total.multiply(value), this.tax.multiply(value))
}

private val cardboardBox = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
      {
        "name": "SPACES",
        "value": 10,
        "units": "NONE",
        "type": "int"
      },
      {
        "name": "QUANTITY",
        "value": 10,
        "units": "PIECES",
        "type": "int"
      }...
     */
    val spacesConfig = getConfig(component, "SPACES", "NONE")
    val quantityConfig = getConfig(component, "QUANTITY", "NONE")

    return if(spacesConfig.value == "10") {
        val kids = sumChildren(component, kidsPrices).multiply(BigDecimal(quantityConfig.value))
        log.info("kids cost $kids")
        val boxPrice = roundAddTaxAndMakePrice(BigDecimal("0.12"))
        kids.add(boxPrice)
    } else throw MissingRuleException("unexpected spaces for cardboard box: ${spacesConfig.value}")
}

private val milk = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
              {
                "name": "VOLUME",
                "value": 950,
                "units": "MILLILITRES",
              },
              {
                "name": "FAT_CONTENT",
                "value": "3.5",
                "units": "PERCENT",
              }...
     */
    val volumeConfig = getConfig(component, "VOLUME", "MILLILITRES")
    val fatConfig = getConfig(component, "FAT_CONTENT", "PERCENT")

    // 4 bucks a litre plus 10 cents per fat content percentage point + random part
    val net = BigDecimal(4).times(BigDecimal(volumeConfig.value)).divide(BigDecimal(1000))
                           .plus(BigDecimal(0.1).times(BigDecimal(fatConfig.value)))
                           .plus(BigDecimal(RANDOM.nextInt(2)).divide(BigDecimal(100)))
    return roundAddTaxAndMakePrice(net)
}

private val butter = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
                 {
                    "name": "WEIGHT",
                    "value": 15,
                    "units": "GRAMS",
                    "type": "int"
                  }...
     */
    val weightConfig = getConfig(component, "WEIGHT", "GRAMS")

    val net = BigDecimal("0.001").times(BigDecimal(weightConfig.value))
    return roundAddTaxAndMakePrice(net)
}

private val sugar = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
                  {
                    "name": "WEIGHT",
                    "value": 15,
                    "units": "GRAMS",
                    "type": "int"
                  },
    */
    val weightConfig = getConfig(component, "WEIGHT", "GRAMS")

    val net = BigDecimal("0.0004").times(BigDecimal(weightConfig.value))
    return roundAddTaxAndMakePrice(net)
}

private val coffeePowder = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
                  {
                    "name": "WEIGHT",
                    "value": 15,
                    "units": "GRAMS",
                    "type": "int"
                  },
    */
    val weightConfig = getConfig(component, "WEIGHT", "GRAMS")

    val net = BigDecimal("0.028").times(BigDecimal(weightConfig.value))
    return roundAddTaxAndMakePrice(net)
}

private val flour = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
                "configs": [
                  {
                    "name": "WEIGHT",
                    "value": 15,
                    "units": "GRAMS",
                    "type": "int"
                  },
    */
    val weightConfig = getConfig(component, "WEIGHT", "GRAMS")

    val net = BigDecimal("0.00012").times(BigDecimal(weightConfig.value))
    return roundAddTaxAndMakePrice(net)
}

private val glassBottle = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>): Price {
    /*
              {
                "name": "VOLUME",
                "value": 1000,
                "units": "MILLILITRES",
                "type": "int"
              },
    */
    val volumeConfig = getConfig(component, "VOLUME", "MILLILITRES")

    return if(volumeConfig.value == "1000") {
        roundAddTaxAndMakePrice(BigDecimal("0.48"))
    } else throw MissingRuleException("unexpected volume for glass bottle : ${volumeConfig.value}")
}

private val sumChildren = fun(component: TreeComponent, kidsPrices: Map<UUID, Price>) =
        component.children.map { kidsPrices[UUID.fromString(it.componentId)]!! }
                .reduce { acc, price -> acc.add(price) }

private fun getConfig(component: TreeComponent, name: String, expectedUnits: String): Configuration {
    val config = component.configs.firstOrNull { it.name == name } ?: throw MissingConfigException("component ${component.componentDefinitionId} is missing config for $name")
    require(config.units == expectedUnits) { "$name has unexpected units ${config.units} instead of $expectedUnits" }
    return config
}

fun roundAddTaxAndMakePrice(unroundedNet: BigDecimal): Price {
    val net = unroundedNet.setScale(2, RoundingMode.HALF_UP)
    val tax = TAX.times(net).setScale(2, RoundingMode.HALF_DOWN)
    val total = net.add(tax)
    return Price(total, tax)
}

class MissingConfigException(msg: String) : RuntimeException(msg)
class MissingRuleException(msg: String) : RuntimeException(msg)

object Prices {
    fun findRule(component: TreeComponent): (TreeComponent, Map<UUID, Price>) -> Price {
        return when (component.componentDefinitionId) {
            "CardboardBox" -> cardboardBox // its not a leaf, but has its own pricing function, as well as that of the children
            "Milk" -> milk
            "Butter" -> butter
            "Sugar" -> sugar
            "Flour" -> flour
            "GlassBottle" -> glassBottle
            "CoffeePowder" -> coffeePowder
            else -> if(component.children.isNotEmpty()) sumChildren // default for things like cookies
                    else throw ValidationException("no pricing rule found for leaf component ${component.componentDefinitionId}")
        }
    }
}
