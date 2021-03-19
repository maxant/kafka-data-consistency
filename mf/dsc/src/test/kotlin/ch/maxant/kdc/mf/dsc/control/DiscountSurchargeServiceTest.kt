package ch.maxant.kdc.mf.dsc.control

import ch.maxant.kdc.mf.dsc.dto.Configuration
import ch.maxant.kdc.mf.dsc.dto.FlatComponent
import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import javax.persistence.EntityManager

class DiscountSurchargeServiceTest {

    private lateinit var sut: DiscountSurchargeService

    @BeforeEach
    fun setup() {
        val em = mock<EntityManager> {  }
        val om = mock<ObjectMapper> {  }
        sut = DiscountSurchargeService(em, om, DraftStateForNonPersistence())
    }

    @Test
    fun toTree() {
        /*
            --- 1
             |--- 2
               |--- 3
             |--- 4
               |--- 5
         */
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val uuid3 = UUID.randomUUID()
        val uuid4 = UUID.randomUUID()
        val uuid5 = UUID.randomUUID()

        val flats = listOf(
                FlatComponent(uuid1, null, "1", listOf(Configuration("c1", "v1", "u1")), ""),
                FlatComponent(uuid2, uuid1, "2", listOf(Configuration("c2", "v2", "u2")), ""),
                FlatComponent(uuid3, uuid2, "3", listOf(Configuration("c3", "v3", "u3")), ""),
                FlatComponent(uuid4, uuid1, "4", listOf(Configuration("c4", "v4", "u4")), ""),
                FlatComponent(uuid5, uuid4, "5", listOf(Configuration("c5", "v5", "u5")), "")
        )

        // when
        val root = sut.toTree(flats)

        // then
        var node = root
        assertEquals(uuid1.toString(), node.componentId)
        assertEquals("1", node.componentDefinitionId)
        assertEquals(1, node.configs.size)
        assertEquals("c1", node.configs[0].name)
        assertEquals("v1", node.configs[0].value)
        assertEquals("u1", node.configs[0].units)
        assertEquals(2, node.children.size)

        node = root.children[0]
        assertEquals(uuid2.toString(), node.componentId)
        assertEquals("2", node.componentDefinitionId)
        assertEquals(1, node.configs.size)
        assertEquals("c2", node.configs[0].name)
        assertEquals("v2", node.configs[0].value)
        assertEquals("u2", node.configs[0].units)
        assertEquals(1, node.children.size)

        node = root.children[0].children[0]
        assertEquals(uuid3.toString(), node.componentId)
        assertEquals("3", node.componentDefinitionId)
        assertEquals(1, node.configs.size)
        assertEquals("c3", node.configs[0].name)
        assertEquals("v3", node.configs[0].value)
        assertEquals("u3", node.configs[0].units)
        assertEquals(0, node.children.size)

        node = root.children[1]
        assertEquals(uuid4.toString(), node.componentId)
        assertEquals("4", node.componentDefinitionId)
        assertEquals(1, node.configs.size)
        assertEquals("c4", node.configs[0].name)
        assertEquals("v4", node.configs[0].value)
        assertEquals("u4", node.configs[0].units)
        assertEquals(1, node.children.size)

        node = root.children[1].children[0]
        assertEquals(uuid5.toString(), node.componentId)
        assertEquals("5", node.componentDefinitionId)
        assertEquals(1, node.configs.size)
        assertEquals("c5", node.configs[0].name)
        assertEquals("v5", node.configs[0].value)
        assertEquals("u5", node.configs[0].units)
        assertEquals(0, node.children.size)
    }

}