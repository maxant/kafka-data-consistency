package ch.maxant.kdc.partners;

import java.util.Objects;

public class ThroughputInitialRecord {

    private String txId;
    private String start;
    private int indexInSet;

    public ThroughputInitialRecord() {
        this(null, null, -1);
    }

    public ThroughputInitialRecord(String txId, String start, int indexInSet) {
        this.txId = txId;
        this.start = start;
        this.indexInSet = indexInSet;
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

    public int getIndexInSet() {
        return indexInSet;
    }

    public void setIndexInSet(int indexInSet) {
        this.indexInSet = indexInSet;
    }

    @Override
    public String toString() {
        return "ThroughputInitialRecord{" +
            "txId='" + txId + '\'' +
            ", start='" + start + '\'' +
            ", indexInSet='" + indexInSet + '\'' +
        '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThroughputInitialRecord that = (ThroughputInitialRecord) o;
        return indexInSet == that.indexInSet &&
                Objects.equals(txId, that.txId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txId, indexInSet);
    }
}
