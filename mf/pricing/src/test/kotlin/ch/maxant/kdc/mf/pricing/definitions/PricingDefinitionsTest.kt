package ch.maxant.kdc.mf.pricing.definitions

import ch.maxant.kdc.mf.pricing.dto.TreeComponent
import ch.maxant.kdc.mf.pricing.dto.Configuration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import javax.validation.ValidationException

class PricingDefinitionsTest {

    @Test
    fun unknownLeafComponent() {
        val milk = TreeComponent("1", "Parent", emptyList(), listOf(TreeComponent(",2", "UnknownChild", emptyList(), emptyList())))
        assertEquals("no pricing rule found for leaf component UnknownChild", assertThrows<ValidationException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkNoVolume() {
        val milk = TreeComponent("1", "Milk", emptyList(), emptyList())
        assertEquals("component Milk is missing config for VOLUME", assertThrows<MissingConfigException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkVolumeWrongUnits() {
        val milk = TreeComponent("1", "Milk", listOf(Configuration("VOLUME", "100", "ASDF")), emptyList())
        assertEquals("VOLUME has unexpected units ASDF instead of MILLILITRES", assertThrows<IllegalArgumentException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkNoFat() {
        val milk = TreeComponent("1", "Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES")), emptyList())
        assertEquals("component Milk is missing config for FAT_CONTENT", assertThrows<MissingConfigException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkFatContentWrongUnits() {
        val milk = TreeComponent("1", "Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES"), Configuration("FAT_CONTENT", "6", "ASDF")), emptyList())
        assertEquals("FAT_CONTENT has unexpected units ASDF instead of PERCENT", assertThrows<IllegalArgumentException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkGood() {
        // 100 ml => 0.40 + 0.1*6 = 1.00 => + tax = 1.08 total
        // + random 10 cents
        val milk = TreeComponent("1", "Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES"), Configuration("FAT_CONTENT", "6", "PERCENT")), emptyList())
        val price = Prices.findRule(milk)(milk)
        assertTrue(BigDecimal("1.08") <= price.total)
        assertTrue(BigDecimal("1.18") >= price.total)
        assertTrue(BigDecimal("0.08") <= price.tax)
        assertTrue(BigDecimal("0.10") >= price.tax)
    }

}