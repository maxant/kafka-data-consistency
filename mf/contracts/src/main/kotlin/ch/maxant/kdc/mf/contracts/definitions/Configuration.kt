package ch.maxant.kdc.mf.contracts.definitions

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.lang.IllegalArgumentException
import java.lang.Integer.parseInt
import java.math.BigDecimal
import java.time.LocalDate


// TODO only works like this at the mo.
// see https://stackoverflow.com/questions/64812745/jackson-not-generating-subtype-information-when-object-is-not-in-a-list
@JsonSerialize(using = ConfigurationSerializer::class)
@JsonDeserialize(using = ConfigurationDeserializer::class)
abstract class Configuration<T> (
        val name: ConfigurableParameter,
        var value: T,
        var units: Units,
        val clazz: Class<T>
) {
    init {
        require(name != ConfigurableParameter.VOLUME || listOf(Units.MILLILITRES).contains(units))
        require(name != ConfigurableParameter.QUANTITY || listOf(Units.NONE).contains(units))
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Configuration<*>) return false

        if (name != other.name) return false
        if (value != other.value) return false
        if (units != other.units) return false
        if (clazz != other.clazz) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + units.hashCode()
        result = 31 * result + clazz.hashCode()
        return result
    }

    override fun toString(): String {
        return "Configuration(name=$name, value=$value, units=$units, clazz=$clazz)"
    }

    fun setValueExplicit(value: Any?) {
        this.value = value as T
    }

}

class ConfigurationSerializer: StdSerializer<Configuration<*>>(Configuration::class.java) {
    override fun serialize(value: Configuration<*>, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("c**", value.javaClass.simpleName)
        gen.writeStringField("value", "${value.value}")
        gen.writeStringField("units", "${value.units}")
        gen.writeStringField("clazz", "${value.clazz.simpleName}")
        gen.writeStringField("name", "${value.name}")
        gen.writeEndObject()
    }
}

class ConfigurationDeserializer: StdDeserializer<Configuration<*>>(Configuration::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Configuration<*> {
        if(p.currentToken == JsonToken.START_OBJECT) {
            var value: String? = null
            var units: Units? = null
            var name: ConfigurableParameter? = null
            var simpleName: String? = null
            while(true) {
                val nt = p.nextToken()
                if(nt == JsonToken.FIELD_NAME) {
                    val cn = p.currentName
                    p.nextToken()
                    val cv = p.text
                    if(cn == "c**") {
                        simpleName = cv
                    } else if(cn == "value") {
                        value = cv
                    } else if(cn == "units") {
                        units = Units.valueOf(cv)
                    } else if(cn == "name") {
                        name = ConfigurableParameter.valueOf(cv)
                    } // else skip it
                } else if(nt == JsonToken.END_OBJECT) {
                    break
                } else {
                    throw IllegalArgumentException("wrong structure, unexpected token $nt")
                }
            }
            return getConfiguration(simpleName!!, name!!, value!!, units!!)
        }
        throw IllegalArgumentException("expected start of object")
    }

}

/** create a new instance based on the given config and given value */
fun getConfiguration(config: Configuration<*>, value: String) = getConfiguration(config.clazz.simpleName, config.name, value, config.units)

/** create a new instance based on the given values */
fun getConfiguration(simpleName: String, name: ConfigurableParameter, value: String, units: Units) =
    when (simpleName) {
        DateConfiguration::class.java.simpleName -> DateConfiguration(name, LocalDate.parse(value))
        StringConfiguration::class.java.simpleName -> StringConfiguration(name, value)
        BigDecimalConfiguration::class.java.simpleName -> BigDecimalConfiguration(name, BigDecimal(value), units)
        IntConfiguration::class.java.simpleName -> IntConfiguration(name, parseInt(value), units)
        PercentConfiguration::class.java.simpleName -> PercentConfiguration(name, BigDecimal(value))
        MaterialConfiguration::class.java.simpleName -> MaterialConfiguration(name, Material.valueOf(value))
        else -> TODO()
    }

open class ConfigurationDefinition<T>(val units: Units, val clazz: Class<T>) {
    fun matches(configuration: Configuration<*>) = configuration.units == units && configuration.clazz == clazz
}

enum class Material {
    MILK, SUGAR, GLASS, FLOUR, BUTTER, CARDBOARD, WOOD, COFFEE_POWDER, VANILLA
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

object PercentRangeConfigurationDefinition : ConfigurationDefinition<BigDecimal>(Units.PERCENT, BigDecimal::class.java)
class PercentRangeConfiguration (
        name: ConfigurableParameter,
        minimum: BigDecimal,
        val maximum: BigDecimal
) : Configuration<BigDecimal>(name, minimum, PercentRangeConfigurationDefinition.units, PercentRangeConfigurationDefinition.clazz)


