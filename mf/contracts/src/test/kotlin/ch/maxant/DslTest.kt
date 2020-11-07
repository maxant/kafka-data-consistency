package ch.maxant

import ch.maxant.kdc.mf.contracts.definitions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DslTest {

    @Test
    fun dsl() {

        val cookiesMilkshake = Milkshake(ProductId.COOKIES_MILKSHAKE, 1000) { qty ->
            listOf(
                    Milk(95 * qty / 100, BigDecimal(6)),
                    Cookies(45 * (qty / 1000)),
                    GlassBottle(qty)
            )
        }
        val box = CardboardBox(CardboardBox.CardboardBoxSize.TEN, 10, cookiesMilkshake)
        val pallet = Pallet(Pallet.PalletSize.ONE_HUNDRED, 50, box)

        fun ComponentDefinition.json(indents: Int): String {
            val sb = StringBuilder("{")

            sb.append("\"type\":\"").append(this.javaClass.simpleName).append("\",")

            // config
            this.configs.forEach {
                sb.append("\"${it.name}\":\"${it.value}\",")
            }

            // children
            sb.append("\"children\":[")
            this.children.forEach {
                sb.append(it.json(indents + 1)).append(",")
            }
            if(this.children.isNotEmpty()) {
                sb.setLength(sb.length - 1) // remove trailing comma
            }
            sb.append("]")

            sb.append("}")
            return sb.toString()
        }
        fun ComponentDefinition.json(): String {
            return this.json(0)
        }

        assertEquals("""
            {"type":"Milkshake","VOLUME":"1000","children":[{"type":"Milk","VOLUME":"950","FAT_CONTENT":"6","MATERIAL":"MILK","children":[]},{"type":"Cookies","WEIGHT":"45","children":[{"type":"Butter","WEIGHT":"15","MATERIAL":"BUTTER","children":[]},{"type":"Sugar","WEIGHT":"15","MATERIAL":"SUGAR","children":[]},{"type":"Flour","WEIGHT":"15","MATERIAL":"FLOUR","children":[]}]},{"type":"GlassBottle","VOLUME":"1000","MATERIAL":"GLASS","children":[]}]}
        """.trimIndent(), cookiesMilkshake.json())

        assertEquals("""
            {"type":"Pallet","SPACES":"100","QUANTITY":"50","MATERIAL":"WOOD","children":[{"type":"CardboardBox","SPACES":"10","QUANTITY":"10","MATERIAL":"CARDBOARD","children":[{"type":"Milkshake","VOLUME":"1000","children":[{"type":"Milk","VOLUME":"950","FAT_CONTENT":"6","MATERIAL":"MILK","children":[]},{"type":"Cookies","WEIGHT":"45","children":[{"type":"Butter","WEIGHT":"15","MATERIAL":"BUTTER","children":[]},{"type":"Sugar","WEIGHT":"15","MATERIAL":"SUGAR","children":[]},{"type":"Flour","WEIGHT":"15","MATERIAL":"FLOUR","children":[]}]},{"type":"GlassBottle","VOLUME":"1000","MATERIAL":"GLASS","children":[]}]}]}]}
        """.trimIndent(), pallet.json())
    }
}

