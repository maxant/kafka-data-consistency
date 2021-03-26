package ch.maxant.kdc.objects;

import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.json.JsonObject;

public class SubscriberModel {
    private String subscriberId;
    private MultiEmitter<? super JsonObject> emitter;

    public void setId(String subscriberId) {
        this.subscriberId = subscriberId;
    }

    public void setEmitter(MultiEmitter<? super JsonObject> emitter) {
        this.emitter = emitter;
    }

    public void emit(JsonObject json) {
        if(!this.emitter.isCancelled()) {
            this.emitter.emit(json);
        }
    }
}
