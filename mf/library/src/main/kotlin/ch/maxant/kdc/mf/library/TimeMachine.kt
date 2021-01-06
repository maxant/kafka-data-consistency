package ch.maxant.kdc.mf.library

import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate
import java.time.LocalDateTime
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class TimeMachine(

        @ConfigProperty(name = "ch.maxant.kdc.mf.library.timemachine.add.days", defaultValue = "0")
        val additionalDays: Long
) {
    fun today() = LocalDate.now().plusDays(additionalDays)

    fun now() = LocalDateTime.now().plusDays(additionalDays)
}