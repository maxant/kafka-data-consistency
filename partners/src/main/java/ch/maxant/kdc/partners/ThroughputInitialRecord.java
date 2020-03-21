package ch.maxant.kdc.partners;

public class ThroughputInitialRecord {

    private String txId;
    private String start;

    public ThroughputInitialRecord() {
    }

    public ThroughputInitialRecord(String txId, String start) {
        this.txId = txId;
        this.start = start;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    @Override
    public String toString() {
        return "ThroughputInitialRecord{" +
                "txId='" + txId + '\'' +
                ", start='" + start + '\'' +
                '}';
    }
}
