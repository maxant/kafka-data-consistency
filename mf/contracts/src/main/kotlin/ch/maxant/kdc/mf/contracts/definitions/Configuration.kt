package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal
import java.time.LocalDate

abstract class Configuration<T> (
        val name: ConfigurableParameter,
        val value: T,
        val units: Units,
        val type: Class<T>
) {
    init {
        require(name != ConfigurableParameter.VOLUME || listOf(Units.MILLILITRES).contains(units))
        require(name != ConfigurableParameter.QUANTITY || listOf(Units.PIECES).contains(units))
        require(name != ConfigurableParameter.WEIGHT || listOf(Units.GRAMS).contains(units))
        require(name != ConfigurableParameter.MATERIAL || listOf(Units.NONE).contains(units))
        require(name != ConfigurableParameter.FAT_CONTENT || listOf(Units.PERCENT).contains(units))
        require(name != ConfigurableParameter.SPACES || listOf(Units.NONE).contains(units))

        if(type == Int::class.java) {
            require(Integer.valueOf(0) <= (value as Integer).toInt())
        } else {
            when (type) {
                BigDecimal::class.java -> require(BigDecimal.ZERO <= value as BigDecimal)
                Material::class.java -> require(Material.values().contains(value as Material))
                else -> throw AssertionError("unknown type $type")
            }
        }
    }
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

class DateConfiguration (
        name: ConfigurableParameter,
        value: LocalDate
) : Configuration<LocalDate>(name, value, Units.NONE, LocalDate::class.java)

class StringConfiguration (
        name: ConfigurableParameter,
        value: String
) : Configuration<String>(name, value, Units.NONE, String::class.java)

class BigDecimalConfiguration (
        name: ConfigurableParameter,
        value: BigDecimal,
        units: Units
) : Configuration<BigDecimal>(name, value, units, BigDecimal::class.java)

class IntConfiguration (
        name: ConfigurableParameter,
        value: Int,
        units: Units
) : Configuration<Int>(name, value, units, Int::class.java)

class PercentConfiguration (
        name: ConfigurableParameter,
        value: BigDecimal
) : Configuration<BigDecimal>(name, value, Units.PERCENT, BigDecimal::class.java)

class MaterialConfiguration (
        name: ConfigurableParameter,
        value: Material
) : Configuration<Material>(name, value, Units.NONE, Material::class.java)


