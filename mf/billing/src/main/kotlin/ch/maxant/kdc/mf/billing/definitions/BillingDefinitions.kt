package ch.maxant.kdc.mf.billing.definitions

class BillingDefinitions {

    companion object {
        fun get(productId: ProductId) = when (productId) {
            ProductId.COOKIES_MILKSHAKE -> {
                // TODO allow customer to actually choose, by having several of these, which the customer can choose from
                BillingDefinition("CM-MD1", Periodicity.MONTHLY, Periodicity.DAILY, 1)
            }
        }
    }

}

data class BillingDefinition(val definitionId: String,
                             val basePeriodicity: Periodicity,
                             val chosenPeriodicity: Periodicity,
                             val referenceDay: Int) {
    init {
        require(referenceDay < 29) { "Reference Day may not be 29, 30 or 31 because that would make billing SUPER complicated" }
        require(referenceDay > 0) { "That makes no sense" }
        require(chosenPeriodicity.numDaysInPeriod <= basePeriodicity.numDaysInPeriod) {
            // see the price determination based on pricing results, in the ContractsConsumer. That would need to return
            // all relevant prices rather than exactly one.
            "Currently, the price must be fixed for at least the length of the bill. In the future we may support billing of periods with different prices."
        }
    }
}

enum class ProductId {
    COOKIES_MILKSHAKE
}

enum class Periodicity(val numDaysInPeriod: Int) {
    YEARLY(360), MONTHLY(30), DAILY(1)
}
