package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.dto.Offer
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.inject.Inject

@ApplicationScoped
class EventBus {

    // TODO how come constructor injection doesnt work with emitter? related to kotlin?
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    lateinit var om: ObjectMapper

    fun publish(event: Event<*>) {
        // TODO set key as contractId
        // TODO transactional outbox
        eventBus.send(om.writeValueAsString(event))
    }
}

enum class Events {
    OFFER_CREATED
}

abstract class Event<T>(val event: Events, open val value: T)

data class OfferEvent(override val value: Offer) : Event<Offer>(Events.OFFER_CREATED, value)