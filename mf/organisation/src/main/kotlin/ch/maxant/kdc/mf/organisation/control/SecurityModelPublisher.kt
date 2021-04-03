package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.library.*
import ch.maxant.kdc.mf.library.ContextInitialised
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject

@ApplicationScoped
class SecurityModelPublisher {

    @Inject // this doesnt appear to work in the constructor
    @Channel("organisation-out")
    lateinit var organisationOut: Emitter<String>

    @Inject
    lateinit var securityDefinitions: SecurityDefinitions

    @Inject
    lateinit var messageBuilder: MessageBuilder

    @Inject
    lateinit var context: Context

    private val log: Logger = Logger.getLogger(Secure::class.java)

    fun init(@Observes e: ContextInitialised) {
        log.info("organisation starting up - publishing latest security model as event")
        val msg = messageBuilder.build(UUID.randomUUID(), securityDefinitions.getDefinitions(), event = "SECURITY_MODEL")
        organisationOut.send(msg)
        log.info("published latest security model as event")
    }
}