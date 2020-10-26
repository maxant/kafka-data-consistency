package ch.maxant.kdc.mf.contracts.definitions

class Profile(
    val quantityMlOfProduct: Int,
    val quantityOfProducts: Int
)

object Profiles {
    val standard = Profile(1000, 10)

    // TODO select based on what we know about the customer
    fun find(): Profile {
        return standard
    }
}