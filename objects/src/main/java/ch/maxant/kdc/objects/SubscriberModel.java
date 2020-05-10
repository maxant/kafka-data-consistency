package ch.maxant.kdc.objects;

import io.smallrye.mutiny.subscription.MultiEmitter;

public class SubscriberModel {
    private String subscriberId;
    private MultiEmitter<? super AnObject> emitter;

    public void setId(String subscriberId) {
        this.subscriberId = subscriberId;
    }

    public void setEmitter(MultiEmitter<? super AnObject> emitter) {
        this.emitter = emitter;
    }

    public void emit(AnObject anObject) {
        if(!this.emitter.isCancelled()) {
            this.emitter.emit(anObject);
        }
    }
}
