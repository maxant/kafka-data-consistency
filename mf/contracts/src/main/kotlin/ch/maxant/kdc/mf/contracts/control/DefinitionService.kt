package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import javax.enterprise.context.ApplicationScoped

private const val DIGITS = "\\d{0,3}"

@ApplicationScoped
@SuppressWarnings("unused")
class DefinitionService {

    fun getMergedDefinitions(root: ComponentDefinition,
                             marketingDefinitions: MarketingDefinitions) =
        MergedComponentDefinition(root, marketingDefinitions)
}

/** allows us to navigate in both directions */
@JsonSerialize(using = MergedComponentDefinitionSerializer::class)
class MergedComponentDefinition(componentDefinition: ComponentDefinition,
                                marketingDefinitions: MarketingDefinitions,
                                private val parent: MergedComponentDefinition? = null):
    AbstractComponentDefinition(componentDefinition.configs,
                                componentDefinition.configPossibilities)
{
    val children = mutableListOf<MergedComponentDefinition>()
    private val defaultComponent: DefaultComponent?
    val componentDefinitionId = componentDefinition.componentDefinitionId
    val productId: ProductId?

    init {
        parent?.children?.add(this)
        defaultComponent = marketingDefinitions.getComponent(getPath())
        cardinalityMin = defaultComponent?.cardinalityMin ?: componentDefinition.cardinalityMin
        cardinalityMax = defaultComponent?.cardinalityMin ?: componentDefinition.cardinalityMin
        productId = if(componentDefinition is Product) componentDefinition.productId else null

        // setup initial config values based on marketing defaults if present
        this.configs = this.configs.map { config ->
            val defaultConfig =
                defaultComponent?.configs?.find { defConfig -> defConfig.name == config.name } ?: null
            if (defaultConfig != null) {
                getConfiguration(config, defaultConfig.value)
            } else {
                config
            }
        }

        // setup config possibilities based on marketing defaults if present
        this.configPossibilities = defaultComponent?.configPossibilities?.map { defaultConfig ->
                // find any config with the same name to be used as a basis, and then use the value from marketing
                val config = this.configs.find { c -> c.name == defaultConfig.name }
                require(config != null) { "Missing basis config for name ${defaultConfig.name} in component $componentDefinitionId" }
                getConfiguration(config, defaultConfig.value)
            } ?: componentDefinition.configPossibilities

        // build rest of tree
        componentDefinition.children.forEach {
            MergedComponentDefinition(it, marketingDefinitions, this)
        }
    }

    fun getPath(): String {
        val name = this.componentDefinitionId
        return if(parent == null) "$name$DIGITS" else "${parent.getPath()}->$name$DIGITS" // works with no cardinality key; "" to "999"
    }

    /** @return the node matching the given path, starting here and working downwards through children */
    fun find(path: String): MergedComponentDefinition? {
        if(Regex(getPath()).matches(path)) {
            return this
        } else {
            for(child in children) {
                val match = child.find(path)
                if (match != null) {
                    return match
                }
            }
        }
        return null
    }
}

class MergedComponentDefinitionSerializer: StdSerializer<MergedComponentDefinition>(MergedComponentDefinition::class.java) {
    override fun serialize(value: MergedComponentDefinition, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeObjectField("configs", value.configs)
        gen.writeObjectField("children", value.children)
        gen.writeObjectField("configPossibilities", value.configPossibilities)
        gen.writeStringField("componentDefinitionId", value.componentDefinitionId)
        gen.writeObjectField("cardinalityMin", value.cardinalityMin)
        gen.writeObjectField("cardinalityMax", value.cardinalityMax)
        gen.writeObjectField("path", value.getPath())
        if(value.rules != null) {
            gen.writeArrayFieldStart("rules")
            value.rules.forEach { gen.writeString(it) }
            gen.writeEndArray()
        }
        if(value.productId != null) {
            gen.writeStringField("productId", value.productId.toString())
        }
        gen.writeEndObject()
    }
}
