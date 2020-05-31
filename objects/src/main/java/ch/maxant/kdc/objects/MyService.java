package ch.maxant.kdc.objects;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class MyService {

    private WebClient webClient;

    @Inject
    Vertx vertx;

    @Inject
    MyContext myContext;

    @PostConstruct
    void init() {
        this.webClient = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost("localhost")
                        .setDefaultPort(8086).setSsl(false).setTrustAll(true));
    }

//    @ContextAware TODO why isnt this failing?
    public CompletableFuture<HttpResponse<Buffer>> makeAsyncCall() {
        return webClient
                .get("/objects/1987bb7d-e02c-4691-a0b3-0dfbaab990be")
                .putHeader("x-token", myContext.getUsername())
                .send()
                .subscribeAsCompletionStage();

    }
}
