package ch.maxant.kdc.mf.pricing.definitions

import ch.maxant.kdc.mf.pricing.dto.Component
import ch.maxant.kdc.mf.pricing.dto.Configuration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import javax.validation.ValidationException

class PricingDefinitionsTest {

    @Test
    fun unknownLeafComponent() {
        val milk = Component("Parent", emptyList(), listOf(Component("UnknownChild", emptyList(), emptyList())))
        assertEquals("no pricing rule found for leaf component UnknownChild", assertThrows<ValidationException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkNoVolume() {
        val milk = Component("Milk", emptyList(), emptyList())
        assertEquals("component Milk is missing config for VOLUME", assertThrows<MissingConfigException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkVolumeWrongUnits() {
        val milk = Component("Milk", listOf(Configuration("VOLUME", "100", "ASDF")), emptyList())
        assertEquals("VOLUME has unexpected units ASDF instead of MILLILITRES", assertThrows<IllegalArgumentException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkNoFat() {
        val milk = Component("Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES")), emptyList())
        assertEquals("component Milk is missing config for FAT_CONTENT", assertThrows<MissingConfigException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkFatContentWrongUnits() {
        val milk = Component("Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES"), Configuration("FAT_CONTENT", "6", "ASDF")), emptyList())
        assertEquals("FAT_CONTENT has unexpected units ASDF instead of PERCENT", assertThrows<IllegalArgumentException> { Prices.findRule(milk)(milk) }.message)
    }

    @Test
    fun milkGood() {
        // 100 ml => 0.40 + 0.1*6 = 1.00 => + tax = 1.08 total
        val milk = Component("Milk", listOf(Configuration("VOLUME", "100", "MILLILITRES"), Configuration("FAT_CONTENT", "6", "PERCENT")), emptyList())
        assertEquals(Price(BigDecimal("1.08"), BigDecimal("0.08")), Prices.findRule(milk)(milk))
    }

}