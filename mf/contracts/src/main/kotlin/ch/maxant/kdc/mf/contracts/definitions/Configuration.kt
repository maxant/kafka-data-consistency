package ch.maxant.kdc.mf.contracts.definitions

import java.math.BigDecimal
import java.time.LocalDate

abstract class Configuration<T> (
        open val name: ConfigurableParameter,
        open val value: T,
        val type: Class<T>
)

enum class Material {
    MILK, SUGAR, EGGS, GLASS
}

enum class ConfigurableParameter {
    WEIGHT_IN_GRAMS, QUANTITY, FAT_CONTENT, MATERIAL
}

class DateConfiguration (
        override val name: ConfigurableParameter,
        override val value: LocalDate
) : Configuration<LocalDate>(name, value, LocalDate::class.java)

class StringConfiguration (
        override val name: ConfigurableParameter,
        override val value: String
) : Configuration<String>(name, value, String::class.java)

class BigDecimalConfiguration (
        override val name: ConfigurableParameter,
        override val value: BigDecimal
) : Configuration<BigDecimal>(name, value, BigDecimal::class.java)

class IntConfiguration (
        override val name: ConfigurableParameter,
        override val value: Int
) : Configuration<Int>(name, value, Int::class.java)

class PercentConfiguration (
        override val name: ConfigurableParameter,
        override val value: BigDecimal
) : Configuration<BigDecimal>(name, value, BigDecimal::class.java)



class MaterialConfiguration (
        override val value: Configuration<*>,
        val material: Material
) : Configuration<Configuration<*>>(ConfigurableParameter.MATERIAL, value, Configuration::class.java)

