package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.pricing.control.PricingRepo
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import java.time.LocalDateTime
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class OffersSink(
        @Inject
        var pricingRepo: PricingRepo
) {

    @Incoming("event-bus-in")
    //TODO how to access the kafka record?
    // fun process(msg: Message<String>): CompletionStage<*> {
    fun process(msg: String) {
        println("\r\nGOT ONE at ${LocalDateTime.now()}: ${msg}")

        // TODO price each one of the lowest ones, using a definition based on
        //  their productComponentId, and sum, up the tree

        // TODO use configs in pricing rules to determine the price

        /*
{"offer":
    {"contract":
        { "id":"82e49c2d-24e3-426b-b20d-b5691f7e44b6",
          "start":"2020-10-26T00:00:00","end":"2022-10-16T00:00:00","status":"DRAFT"},
        "pack":
            { "componentDefinitionId":"CardboardBox",
              "configs":[
                {"name":"SPACES","value":10,"units":"NONE","type":"int"},
                {"name":"QUANTITY","value":10,"units":"PIECES","type":"int"},
                {"name":"MATERIAL","value":"CARDBOARD","units":"NONE","type":"ch.maxant.kdc.mf.contracts.definitions.Material"}],
                "children":[
                    {"productId":"COOKIES_MILKSHAKE","componentDefinitionId":"Milkshake",
        */

        /*
        println("GOT ONE: ${msg.payload}")
        val metadata = msg.getMetadata(IncomingKafkaRecordMetadata::class.java)
        if(metadata.isPresent) {
            println("metadata: ofset: ${metadata.get().offset} / key: ${metadata.get().key} / topic: ${metadata.get().topic} / partition: ${metadata.get().partition}")
        } else {
            println("no metadata")
        }

        return msg.ack()
         */
    }

}