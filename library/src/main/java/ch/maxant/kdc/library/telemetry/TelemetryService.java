package ch.maxant.kdc.library.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A bean which can be injected and used to measure the time that a call took within a component
 */
@RequestScoped
public class TelemetryService {

    public static final String X_SESSION_ID = "x-session-id";
    public static final String X_ACTION_ID = "x-action-id";
    public static final String X_CALL_ID = "x-call-id";
    public static final String X_SOURCE = "x-source";
    private long start;

    @Inject
    @ComponentName
    String componentName;

    @Inject
    ObjectMapper om;

    @Context
    private HttpServletRequest httpRequest;

    private TelemetryRecord incomingRecord;

    @PostConstruct
    public void init() {
        if(httpRequest != null) {
            incomingRecord = new TelemetryRecord(
                httpRequest.getHeader(X_SESSION_ID),
                httpRequest.getHeader(X_ACTION_ID),
                httpRequest.getHeader(X_CALL_ID),
                httpRequest.getHeader(X_SOURCE),
                componentName
            );
            this.start = (long)httpRequest.getAttribute("start");
        }
    }

    public void startFromKafka(ConsumerRecord<String, String> r) {
        this.start = System.currentTimeMillis();

        incomingRecord = new TelemetryRecord(
                new String(r.headers().headers(X_SESSION_ID).iterator().next().value(), UTF_8),
                new String(r.headers().headers(X_ACTION_ID).iterator().next().value(), UTF_8),
                new String(r.headers().headers(X_CALL_ID).iterator().next().value(), UTF_8),
                KAFKA,
                componentName
        );

    }

    public void toKafka(List<ProducerRecord<String, String>> records) {

        TelemetryRecord telemetryRecord = new TelemetryRecord(incomingRecord, componentName, KAFKA);
        telemetryRecord.setDuration(System.currentTimeMillis() - start);

        records.forEach(r -> {
            r.headers().add(X_SESSION_ID, telemetryRecord.getSession().getBytes(UTF_8));
            r.headers().add(X_ACTION_ID, telemetryRecord.getAction().getBytes(UTF_8));
            r.headers().add(X_CALL_ID, telemetryRecord.getCall().getBytes(UTF_8));
            r.headers().add(X_FROM, telemetryRecord.getFrom().getBytes(UTF_8));
            r.headers().add(X_TO, telemetryRecord.getTo().getBytes(UTF_8));
        });
    }

    public TelemetryRecord getIncomingRecord() {
        return incomingRecord;
    }

    public void setIncomingRecord(TelemetryRecord incomingRecord) {
        this.incomingRecord = incomingRecord;
    }

    public void fromKafkaToKafka(ConsumerRecord<String, String> r, long start) {

    }

    public void log(long start) {
        TelemetryRecord telemetryRecord = new TelemetryRecord(incomingRecord, componentName, KAFKA);
        telemetryRecord.setDuration(System.currentTimeMillis() - start);
        log(telemetryRecord);
    }

    private void log(TelemetryRecord telemetryRecord) {
        try {
            System.out.println("TELEMETRY::" + om.writeValueAsString(telemetryRecord) + "::");
        } catch(JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
