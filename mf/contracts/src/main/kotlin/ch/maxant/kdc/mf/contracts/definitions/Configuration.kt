package ch.maxant.kdc.mf.contracts.definitions

import org.apache.commons.lang3.Validate.isTrue
import java.math.BigDecimal
import java.time.LocalDate

abstract class Configuration<T> (
        val name: ConfigurableParameter,
        val value: T,
        val units: Units,
        val type: Class<T>
) {
    init {
        isTrue(!name.equals(ConfigurableParameter.VOLUME) || listOf(Units.MILLILITRES).contains(units))
        isTrue(!name.equals(ConfigurableParameter.QUANTITY) || listOf(Units.PIECES).contains(units))
        isTrue(!name.equals(ConfigurableParameter.WEIGHT) || listOf(Units.GRAMS).contains(units))
        isTrue(!name.equals(ConfigurableParameter.MATERIAL) || listOf(Units.NONE).contains(units))
        isTrue(!name.equals(ConfigurableParameter.FAT_CONTENT) || listOf(Units.PERCENT).contains(units))
        isTrue(!name.equals(ConfigurableParameter.SPACES) || listOf(Units.NONE).contains(units))

        if(type == Int::class.java) {
            isTrue(Integer.valueOf(0).compareTo((value as Integer).toInt()) <= 0)
        } else {
            when (type) {
                BigDecimal::class.java -> isTrue(BigDecimal.ZERO.compareTo(value as BigDecimal) <= 0)
                Material::class.java -> isTrue(Material.values().contains(value as Material))
                else -> throw AssertionError("unknown type " + type)
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


