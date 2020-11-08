package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
        val contract = ContractEntity(UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MAX, ContractState.DRAFT)
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
    fun updateConfig() {
        val draft = setup()
        val milk = draft.pack.getThisAndAllChildren().find { it.componentDefinitionId == Milk::class.java.simpleName } !!

        // when
        val c = flushed(em) {
            sut.updateConfig(draft.contract.id, milk.componentId!!, ConfigurableParameter.FAT_CONTENT, "7")
        }

        // then
        val component = em.find(ComponentEntity::class.java, milk.componentId)
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        val config = configs.find { it.name == ConfigurableParameter.FAT_CONTENT } !!
        assertEquals(BigDecimal("7"), config.value)
        assertEquals(BigDecimal::class.java, config.clazz)
        assertEquals(ConfigurableParameter.FAT_CONTENT, config.name)
        assertEquals(Units.PERCENT, config.units)
        assertEquals(config.clazz, c.clazz)
        assertEquals(config.name, c.name)
        assertEquals(config.value, c.value)
        assertEquals(config.units, c.units)
    }

}

// TODO delete this in lieu of the one in the library
fun <T> flushed(em: EntityManager, f: ()->T) =
    try {
        f()
    } catch(e: Exception) {
        throw e
    } finally {
        em.flush()
        em.clear()
    }
