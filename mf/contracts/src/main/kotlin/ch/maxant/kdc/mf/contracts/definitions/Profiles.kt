package ch.maxant.kdc.mf.contracts.definitions

class Profile(
    val id: ProfileId,
    val quantityMlOfProduct: Int,
    val quantityOfProducts: Int,
    val interestedInStrongFlavours: Boolean
)

enum class ProfileId {
    STANDARD
}

object Profiles {

    private val standard = Profile(ProfileId.STANDARD, 1000, 10, true)

    // TODO select based on what we know about the customer
    fun find(): Profile {
        return standard
    }

    fun get(profileId: ProfileId) = when(profileId) {
        ProfileId.STANDARD -> standard
    }
}