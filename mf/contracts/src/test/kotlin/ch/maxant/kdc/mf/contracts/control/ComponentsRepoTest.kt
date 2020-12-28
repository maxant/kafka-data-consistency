package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.TestUtils
import ch.maxant.kdc.mf.library.TestUtils.Companion.flushed
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@QuarkusTest
@Transactional
class ComponentsRepoTest {

    @Inject
    lateinit var em: EntityManager

    @Inject
    lateinit var sut: ComponentsRepo

    @Inject
    lateinit var om: ObjectMapper

    fun setup(): Draft = flushed(em) {
        val contract = ContractEntity(UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MAX, "fred")
        em.persist(contract)
        val profile: Profile = Profiles.find()
        val product = Products.find(ProductId.COOKIES_MILKSHAKE, profile.quantityMlOfProduct)
        val pack = Packagings.pack(profile.quantityOfProducts, product)
        sut.saveInitialDraft(contract.id, pack)
        em.flush()
        em.clear()
        Draft(contract, pack)
    }

    @Test
    fun create() {
        val draft = setup()

        // TODO assertions
    }

    @Test
    fun updateConfig_happy() {
        val draft = setup()
        val milk = draft.pack.getThisAndAllChildren().find { it.componentDefinitionId == Milk::class.java.simpleName } !!

        // when
        val c = flushed(em) {
            sut.updateConfig(draft.contract.id, milk.componentId!!, ConfigurableParameter.FAT_CONTENT, "0.2")
        }

        // then - check whats in the result
        val comps = c.filter { it.componentDefinitionId == milk.componentDefinitionId }
        assertEquals(1, comps.size)
        assertEquals(milk.componentDefinitionId, comps[0].componentDefinitionId)
        assertEquals(milk.componentId, comps[0].id)
        assertEquals(milk.configs.filterNot { it.name == ConfigurableParameter.FAT_CONTENT }, comps[0].configs.filterNot { it.name == ConfigurableParameter.FAT_CONTENT })
        val newMilk = comps[0].configs.first { it.name == ConfigurableParameter.FAT_CONTENT }
        assertEquals(BigDecimal("0.2"), newMilk.value)
        assertEquals(ConfigurableParameter.FAT_CONTENT, newMilk.name)
        assertEquals(Units.PERCENT, newMilk.units)

        // then - check whats in the DB
        val component = em.find(ComponentEntity::class.java, milk.componentId)
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        val config = configs.find { it.name == ConfigurableParameter.FAT_CONTENT } !!
        assertEquals(BigDecimal("0.2"), config.value)
        assertEquals(BigDecimal::class.java, config.clazz)
        assertEquals(ConfigurableParameter.FAT_CONTENT, config.name)
        assertEquals(Units.PERCENT, config.units)
        assertEquals(8, c.size)
    }

    @Test
    fun updateConfig_illegalValue() {
        val draft = setup()
        val milk = draft.pack.getThisAndAllChildren().find { it.componentDefinitionId == Milk::class.java.simpleName } !!

        // when / then
        assertEquals("Component configuration value 7 is not in the permitted set of values [0.2, 1.8, 3.5, 6.0]",
                assertThrows<IllegalArgumentException> { sut.updateConfig(draft.contract.id, milk.componentId!!, ConfigurableParameter.FAT_CONTENT, "7") }.message)
    }

}

