package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal
import java.time.LocalDate

abstract class Configuration<T> (
        val name: ConfigurableParameter,
        var value: T,
        var units: Units,
        val clazz: Class<T>
) {
    init {
        require(name != ConfigurableParameter.VOLUME || listOf(Units.MILLILITRES).contains(units))
        require(name != ConfigurableParameter.QUANTITY || listOf(Units.PIECES).contains(units))
        require(name != ConfigurableParameter.WEIGHT || listOf(Units.GRAMS).contains(units))
        require(name != ConfigurableParameter.MATERIAL || listOf(Units.NONE).contains(units))
        require(name != ConfigurableParameter.FAT_CONTENT || listOf(Units.PERCENT).contains(units))
        require(name != ConfigurableParameter.SPACES || listOf(Units.NONE).contains(units))

        if(clazz == Int::class.java) {
            require(Integer.valueOf(0) <= (value as Integer).toInt())
        } else {
            when (clazz) {
                BigDecimal::class.java -> require(BigDecimal.ZERO <= value as BigDecimal)
                Material::class.java -> require(Material.values().contains(value as Material))
                else -> throw AssertionError("unknown type $clazz")
            }
        }
    }
}

open class ConfigurationDefinition<T>(val units: Units, val clazz: Class<T>) {
    fun matches(configuration: Configuration<*>) = configuration.units == units && configuration.clazz == clazz
}

enum class Material {
    MILK, SUGAR, EGGS, GLASS, FLOUR, BUTTER, CARDBOARD, WOOD
}

enum class Units {
    GRAMS, MILLILITRES, PERCENT, NONE, PIECES
}

enum class ConfigurableParameter {
    QUANTITY, FAT_CONTENT, MATERIAL, WEIGHT, VOLUME, SPACES
}

object DateConfigurationDefinition : ConfigurationDefinition<LocalDate>(Units.NONE, LocalDate::class.java)
class DateConfiguration (
        name: ConfigurableParameter,
        value: LocalDate
) : Configuration<LocalDate>(name, value, DateConfigurationDefinition.units, DateConfigurationDefinition.clazz)

object StringConfigurationDefinition : ConfigurationDefinition<String>(Units.NONE, String::class.java)
class StringConfiguration (
        name: ConfigurableParameter,
        value: String
) : Configuration<String>(name, value, StringConfigurationDefinition.units, StringConfigurationDefinition.clazz)

object BigDecimalConfigurationDefinition : ConfigurationDefinition<BigDecimal>(Units.NONE, BigDecimal::class.java)
class BigDecimalConfiguration (
        name: ConfigurableParameter,
        value: BigDecimal,
        units: Units
) : Configuration<BigDecimal>(name, value, units, BigDecimalConfigurationDefinition.clazz)

object IntConfigurationDefinition : ConfigurationDefinition<Int>(Units.NONE, Int::class.java)
class IntConfiguration (
        name: ConfigurableParameter,
        value: Int,
        units: Units
) : Configuration<Int>(name, value, units, IntConfigurationDefinition.clazz)

object PercentConfigurationDefinition : ConfigurationDefinition<BigDecimal>(Units.PERCENT, BigDecimal::class.java)
class PercentConfiguration (
        name: ConfigurableParameter,
        value: BigDecimal
) : Configuration<BigDecimal>(name, value, PercentConfigurationDefinition.units, PercentConfigurationDefinition.clazz)

object MaterialConfigurationDefinition : ConfigurationDefinition<Material>(Units.NONE, Material::class.java)
class MaterialConfiguration (
        name: ConfigurableParameter,
        value: Material
) : Configuration<Material>(name, value, MaterialConfigurationDefinition.units, MaterialConfigurationDefinition.clazz)


