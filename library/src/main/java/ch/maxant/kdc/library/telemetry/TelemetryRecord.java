package ch.maxant.kdc.library.telemetry;

/**
 * session - the users session, to be able to track everything they do within a session
 * action  - the users action which lead to the call being recorded
 * call    - the call
 */
public class TelemetryRecord {
    private String sessionId;
    private String actionId;
    private String callId;
    private String callingComponent;
    private String currentcomponent;
    private String methodBeingMeasured;
    private long duration;
    // timestamp comes from log


    places which are interesting:

    interceptor in general
    incoming rest => propagation + start
    incoming kafka => propagation + start
    outgoing rest => propagation
    outgoing kafka => propagation

    use interceptor to measure duration of kafka too

    telemetry service can take incoming record from rest if its available, otherwise it has to have been set by the kafka adapter in the library


    public TelemetryRecord(String session, String action, String call, String from, String to) {
        this.session = session;
        this.action = action;
        this.call = call;
        this.from = from;
        this.to = to;
    }

    public TelemetryRecord(TelemetryRecord incomingRecord, String from, String to) {
        this(incomingRecord.session, incomingRecord.action, incomingRecord.call, from, to);
    }

    public String getSession() {
        return session;
    }

    public String getAction() {
        return action;
    }

    public String getCall() {
        return call;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public void setDuration(long ms) {
        this.duration = ms;
    }

    public long getDuration() {
        return duration;
    }
}
