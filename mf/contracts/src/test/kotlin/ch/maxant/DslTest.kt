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
        val box = CardboardBox(CardboardBox.CardboardBoxSize.TEN, 10, cookiesMilkshake)
        val pallet = Pallet(Pallet.PalletSize.ONE_HUNDRED, 50, box)

        assertEquals("""
            {"type":"Milkshake","VOLUME":"1000","children":[{"type":"Milk","VOLUME":"950","FAT_CONTENT":"1.8","MATERIAL":"MILK","children":[]},{"type":"Cookies","WEIGHT":"45","children":[{"type":"Butter","WEIGHT":"15","MATERIAL":"BUTTER","children":[]},{"type":"Sugar","WEIGHT":"15","MATERIAL":"SUGAR","children":[]},{"type":"Flour","WEIGHT":"15","MATERIAL":"FLOUR","children":[]}]},{"type":"GlassBottle","VOLUME":"1000","MATERIAL":"GLASS","children":[]}]}
        """.trimIndent(), JacksonConfig.om.writeValueAsString(cookiesMilkshake))

        assertEquals("""
            {"type":"Pallet","SPACES":"100","QUANTITY":"50","MATERIAL":"WOOD","children":[{"type":"CardboardBox","SPACES":"10","QUANTITY":"10","MATERIAL":"CARDBOARD","children":[{"type":"Milkshake","VOLUME":"1000","children":[{"type":"Milk","VOLUME":"950","FAT_CONTENT":"6","MATERIAL":"MILK","children":[]},{"type":"Cookies","WEIGHT":"45","children":[{"type":"Butter","WEIGHT":"15","MATERIAL":"BUTTER","children":[]},{"type":"Sugar","WEIGHT":"15","MATERIAL":"SUGAR","children":[]},{"type":"Flour","WEIGHT":"15","MATERIAL":"FLOUR","children":[]}]},{"type":"GlassBottle","VOLUME":"1000","MATERIAL":"GLASS","children":[]}]}]}]}
        """.trimIndent(), JacksonConfig.om.writeValueAsString(pallet))
    }

    @Test
    fun deserSubclasses() {
        val product = Products.find(ProductId.COOKIES_MILKSHAKE, 1000)
        val om = JacksonConfig.om
        val s = om.writeValueAsString(product.configs)
        println(s)
        val o1 = om.readValue<ArrayList<Configuration<*>>>(s)
        val s1 = om.writeValueAsString(product.configs[0])
        println(s1)
        val o2 = om.readValue<Configuration<*>>(s1)
        assertEquals("""[{"@c":"ch.maxant.kdc.mf.contracts.definitions.IntConfiguration","name":"VOLUME","value":1000,"units":"MILLILITRES","clazz":"int"}]""", s)
        val configs = om.readValue<ArrayList<Configuration<*>>>(s) // <<<<< note how it has to be an subclass of list, rather than a list! - this seems to be a kotlin problem
        assertEquals(1, configs.size)
        assertEquals(Int::class.java, configs[0].clazz)
        assertEquals(Units.MILLILITRES, configs[0].units)
        assertEquals(1000, configs[0].value)
        assertEquals(ConfigurableParameter.VOLUME, configs[0].name)
    }

    @Test
    fun deserModel() {
        val product = Products.find(ProductId.COOKIES_MILKSHAKE, 1000)
        val om = JacksonConfig.om
        val s = om.writeValueAsString(product)
        println(s)
        assertEquals("""[{"@c":"ch.maxant.kdc.mf.contracts.definitions.IntConfiguration","name":"VOLUME","value":1000,"units":"MILLILITRES","clazz":"int"}]""", s)
    }
}

