package ch.maxant.kdc.mf.billing.definitions

class BillingDefinitions {

    companion object {
        fun get(productId: ProductId) = when (productId) {
            ProductId.COOKIES_MILKSHAKE -> {
                BillingDefinition(Periodicity.MONTHLY, Periodicity.DAILY, 1)
            }
        }
    }

}

data class BillingDefinition(val basePeriodicity: Periodicity,
                             val chosenPeriodicity: Periodicity,
                             val referenceDay: Int) {
    init {
        require(referenceDay < 29) { "Reference Day may not be 29, 30 or 31 because that would make billing SUPER complicated" }
        require(referenceDay > 0) { "That makes no sense" }
    }
}

enum class ProductId {
    COOKIES_MILKSHAKE
}

enum class Periodicity {
    YEARLY, MONTHLY, DAILY
}
