package ch.maxant.kdc.mf.contracts.definitions

class Profile(val quantityMl: Int) {
}

object Profiles {
    val standard = Profile(1000)

    // TODO select based on what we know about the customer
    fun find(): Profile {
        return standard
    }
}