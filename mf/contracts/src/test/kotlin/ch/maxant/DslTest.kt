package ch.maxant

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.library.JacksonConfig
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DslTest {

    @Test
    fun dsl() {

        val cookiesMilkshake = Milkshake(ProductId.COOKIES_MILKSHAKE, 1000) { qty ->
            listOf(
                    Milk(95 * qty / 100, BigDecimal("1.8")),
                    Cookies(45 * (qty / 1000)),
                    GlassBottle(qty)
            )
        }
        assertEquals("""
            {"c**":"Milkshake","configs":[{"c**":"IntConfiguration","value":"1000","units":"MILLILITRES","clazz":"int","name":"VOLUME"}],"children":[{"c**":"Milk","configs":[{"c**":"IntConfiguration","value":"950","units":"MILLILITRES","clazz":"int","name":"VOLUME"},{"c**":"PercentConfiguration","value":"1.8","units":"PERCENT","clazz":"BigDecimal","name":"FAT_CONTENT"},{"c**":"MaterialConfiguration","value":"MILK","units":"NONE","clazz":"Material","name":"MATERIAL"}],"children":[],"configPossibilities":[{"c**":"PercentConfiguration","value":"0.2","units":"PERCENT","clazz":"BigDecimal","name":"FAT_CONTENT"},{"c**":"PercentConfiguration","value":"1.8","units":"PERCENT","clazz":"BigDecimal","name":"FAT_CONTENT"},{"c**":"PercentConfiguration","value":"3.5","units":"PERCENT","clazz":"BigDecimal","name":"FAT_CONTENT"}],"componentDefinitionId":"Milk","componentId":null},{"c**":"Cookies","configs":[{"c**":"IntConfiguration","value":"45","units":"GRAMS","clazz":"int","name":"WEIGHT"}],"children":[{"c**":"Butter","configs":[{"c**":"IntConfiguration","value":"15","units":"GRAMS","clazz":"int","name":"WEIGHT"},{"c**":"MaterialConfiguration","value":"BUTTER","units":"NONE","clazz":"Material","name":"MATERIAL"}],"children":[],"configPossibilities":[],"componentDefinitionId":"Butter","componentId":null},{"c**":"Sugar","configs":[{"c**":"IntConfiguration","value":"15","units":"GRAMS","clazz":"int","name":"WEIGHT"},{"c**":"MaterialConfiguration","value":"SUGAR","units":"NONE","clazz":"Material","name":"MATERIAL"}],"children":[],"configPossibilities":[],"componentDefinitionId":"Sugar","componentId":null},{"c**":"Flour","configs":[{"c**":"IntConfiguration","value":"15","units":"GRAMS","clazz":"int","name":"WEIGHT"},{"c**":"MaterialConfiguration","value":"FLOUR","units":"NONE","clazz":"Material","name":"MATERIAL"}],"children":[],"configPossibilities":[],"componentDefinitionId":"Flour","componentId":null}],"configPossibilities":[],"componentDefinitionId":"Cookies","componentId":null},{"c**":"GlassBottle","configs":[{"c**":"IntConfiguration","value":"1000","units":"MILLILITRES","clazz":"int","name":"VOLUME"},{"c**":"MaterialConfiguration","value":"GLASS","units":"NONE","clazz":"Material","name":"MATERIAL"}],"children":[],"configPossibilities":[],"componentDefinitionId":"GlassBottle","componentId":null}],"configPossibilities":[],"componentDefinitionId":"Milkshake","componentId":null,"productId":"COOKIES_MILKSHAKE"}
        """.trimIndent(), JacksonConfig.om.writeValueAsString(cookiesMilkshake))
    }

    @Test
    fun deserSubclasses() {
        val product = Products.find(ProductId.COOKIES_MILKSHAKE, 1000)
        val om = JacksonConfig.om
        val s = om.writeValueAsString(product.configs)
        println(s)
        assertEquals("""[{"c**":"IntConfiguration","value":"1000","units":"MILLILITRES","clazz":"int","name":"VOLUME"}]""", s)

        // now do just one config, as this doesnt work by default:
        // https://stackoverflow.com/questions/64812745/jackson-not-generating-subtype-information-when-object-is-not-in-a-list
        val s1 = om.writeValueAsString(product.configs[0])
        println(s1)
        assertEquals("""{"c**":"IntConfiguration","value":"1000","units":"MILLILITRES","clazz":"int","name":"VOLUME"}""", s1)

        // check we can deserialise an array:
        val configs = om.readValue<List<Configuration<*>>>(s) // <<<<< if this fails, try readValue<ArrayList<Configuration<*>>>(s)

        // check we can deserialise just one element:
        val o2 = om.readValue<Configuration<*>>(s1)

        assertEquals(1, configs.size)
        assertEquals(Int::class.java, configs[0].clazz)
        assertEquals(Units.MILLILITRES, configs[0].units)
        assertEquals(1000, configs[0].value)
        assertEquals(ConfigurableParameter.VOLUME, configs[0].name)
    }

    @Test
    fun deserPackaging() {
        val packaging = Packagings.pack(10, Products.find(ProductId.COOKIES_MILKSHAKE, 1000))
        val om = JacksonConfig.om
        val s = om.writeValueAsString(packaging)
        println(s)
        assertEquals(expectedJsonOfPackaging, s)
        // TODO havent written the deserializer yet, as its complicated because of the component constructors which
        // require children to exist before being constructed, meaning i need to navigate down the tree and start
        // at the leaves. but I dont actually have a use case for that yet...
        // val pack = om.readValue<Packaging>(s)
        // assertEquals(1, pack.componentDefinitionId, CardboardBox::class.java.simpleName)
    }
}

private val expectedJsonOfPackaging = """
{
  "c**": "CardboardBox",
  "configs": [
    {
      "c**": "IntConfiguration",
      "value": "10",
      "units": "NONE",
      "clazz": "int",
      "name": "SPACES"
    },
    {
      "c**": "IntConfiguration",
      "value": "10",
      "units": "PIECES",
      "clazz": "int",
      "name": "QUANTITY"
    },
    {
      "c**": "MaterialConfiguration",
      "value": "CARDBOARD",
      "units": "NONE",
      "clazz": "Material",
      "name": "MATERIAL"
    }
  ],
  "children": [
    {
      "c**": "Milkshake",
      "configs": [
        {
          "c**": "IntConfiguration",
          "value": "1000",
          "units": "MILLILITRES",
          "clazz": "int",
          "name": "VOLUME"
        }
      ],
      "children": [
        {
          "c**": "Milk",
          "configs": [
            {
              "c**": "IntConfiguration",
              "value": "950",
              "units": "MILLILITRES",
              "clazz": "int",
              "name": "VOLUME"
            },
            {
              "c**": "PercentConfiguration",
              "value": "1.8",
              "units": "PERCENT",
              "clazz": "BigDecimal",
              "name": "FAT_CONTENT"
            },
            {
              "c**": "MaterialConfiguration",
              "value": "MILK",
              "units": "NONE",
              "clazz": "Material",
              "name": "MATERIAL"
            }
          ],
          "children": [],
          "configPossibilities": [
            {
              "c**": "PercentConfiguration",
              "value": "0.2",
              "units": "PERCENT",
              "clazz": "BigDecimal",
              "name": "FAT_CONTENT"
            },
            {
              "c**": "PercentConfiguration",
              "value": "1.8",
              "units": "PERCENT",
              "clazz": "BigDecimal",
              "name": "FAT_CONTENT"
            },
            {
              "c**": "PercentConfiguration",
              "value": "3.5",
              "units": "PERCENT",
              "clazz": "BigDecimal",
              "name": "FAT_CONTENT"
            }
          ],
          "componentDefinitionId": "Milk",
          "componentId": null
        },
        {
          "c**": "Cookies",
          "configs": [
            {
              "c**": "IntConfiguration",
              "value": "45",
              "units": "GRAMS",
              "clazz": "int",
              "name": "WEIGHT"
            }
          ],
          "children": [
            {
              "c**": "Butter",
              "configs": [
                {
                  "c**": "IntConfiguration",
                  "value": "15",
                  "units": "GRAMS",
                  "clazz": "int",
                  "name": "WEIGHT"
                },
                {
                  "c**": "MaterialConfiguration",
                  "value": "BUTTER",
                  "units": "NONE",
                  "clazz": "Material",
                  "name": "MATERIAL"
                }
              ],
              "children": [],
              "configPossibilities": [],
              "componentDefinitionId": "Butter",
              "componentId": null
            },
            {
              "c**": "Sugar",
              "configs": [
                {
                  "c**": "IntConfiguration",
                  "value": "15",
                  "units": "GRAMS",
                  "clazz": "int",
                  "name": "WEIGHT"
                },
                {
                  "c**": "MaterialConfiguration",
                  "value": "SUGAR",
                  "units": "NONE",
                  "clazz": "Material",
                  "name": "MATERIAL"
                }
              ],
              "children": [],
              "configPossibilities": [],
              "componentDefinitionId": "Sugar",
              "componentId": null
            },
            {
              "c**": "Flour",
              "configs": [
                {
                  "c**": "IntConfiguration",
                  "value": "15",
                  "units": "GRAMS",
                  "clazz": "int",
                  "name": "WEIGHT"
                },
                {
                  "c**": "MaterialConfiguration",
                  "value": "FLOUR",
                  "units": "NONE",
                  "clazz": "Material",
                  "name": "MATERIAL"
                }
              ],
              "children": [],
              "configPossibilities": [],
              "componentDefinitionId": "Flour",
              "componentId": null
            }
          ],
          "configPossibilities": [],
          "componentDefinitionId": "Cookies",
          "componentId": null
        },
        {
          "c**": "GlassBottle",
          "configs": [
            {
              "c**": "IntConfiguration",
              "value": "1000",
              "units": "MILLILITRES",
              "clazz": "int",
              "name": "VOLUME"
            },
            {
              "c**": "MaterialConfiguration",
              "value": "GLASS",
              "units": "NONE",
              "clazz": "Material",
              "name": "MATERIAL"
            }
          ],
          "children": [],
          "configPossibilities": [],
          "componentDefinitionId": "GlassBottle",
          "componentId": null
        }
      ],
      "configPossibilities": [],
      "componentDefinitionId": "Milkshake",
      "componentId": null,
      "productId": "COOKIES_MILKSHAKE"
    }
  ],
  "configPossibilities": [],
  "componentDefinitionId": "CardboardBox",
  "componentId": null
}
""".trimIndent().replace(" ", "").replace("\r", "").replace("\n", "")
