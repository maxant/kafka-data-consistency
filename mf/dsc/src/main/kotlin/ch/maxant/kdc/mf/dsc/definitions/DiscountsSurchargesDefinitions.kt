package ch.maxant.kdc.mf.dsc.definitions

import ch.maxant.kdc.mf.dsc.dto.TreeComponent
import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity
import java.math.BigDecimal
import java.util.*

object DiscountsSurchargesDefinitions {

    fun determineDiscountsSurcharges(pack: TreeComponent): List<DiscountSurchargeEntity> {
        val discountsSurcharges = mutableListOf<DiscountSurchargeEntity>()
        if(pack.componentDefinitionId == "CardboardBox") {
            if(pack.children.all { it.productId == "COOKIES_MILKSHAKE" }) {
                if(pack.configs.filter { it.name == "QUANTITY" }.map { it.value.toInt() }.sum() >= 10) {
                    discountsSurcharges.add(
                        DiscountSurchargeEntity(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.fromString(pack.componentId),
                            "CM_Q_GE_10",
                            BigDecimal("0.05").negate(),
                            0,
                            false)
                    )
                }
            }
        }

        // add other discounts here...

        return discountsSurcharges
    }
}

