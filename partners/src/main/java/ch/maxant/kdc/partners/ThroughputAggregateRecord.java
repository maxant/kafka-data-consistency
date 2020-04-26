package ch.maxant.kdc.partners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ThroughputAggregateRecord {

    private String id;
    private String txId;
    private Set<ThroughputInitialRecord> records = initialiseThroughputInitialRecords();
    private String windowStart;
    private String windowEnd;
    private String firstAggregation;
    private String lastAggregation;
    private int numOfMerges;
    private String finalMappingTime;

    private TreeSet<ThroughputInitialRecord> initialiseThroughputInitialRecords() {
        return new TreeSet<>(Comparator.comparing(ThroughputInitialRecord::getIndexInSet));
    }

    private String firstAdded = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private String lastAdded = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    public ThroughputInitialRecord addRecord(ThroughputInitialRecord r) {
        setTxId(r.getTxId());
        records.add(r);
        lastAdded = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return r;
    }

    public String getId() {
        return id;
    }

    public Set<ThroughputInitialRecord> getRecords() {
        return records;
    }

    public void setId(String id) {
        if(this.id != null) {
            if(!this.id.equals(id)) throw new RuntimeException("keys not same");
        } else {
            this.id = id;
        }
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getTxId() {
        return txId;
    }

    public String getEarliestCreated() {
        return records.stream().map(ThroughputInitialRecord::getStart).sorted().findFirst().orElse(null);
    }

    public String getLatestCreated() {
        return records.stream().map(ThroughputInitialRecord::getStart).sorted(Comparator.reverseOrder()).findFirst().orElse(null);
    }

    public String getFirstAdded() {
        return firstAdded;
    }

    public String getLastAdded() {
        return lastAdded;
    }

    public int getNumRecordsProcessed() {
        return records.size();
    }

    public void merge(ThroughputAggregateRecord that) {
        if(this.firstAdded != null) {
            if(that.firstAdded != null) {
                this.firstAdded = this.firstAdded.compareTo(that.firstAdded) < 0 ? this.firstAdded : that.firstAdded;
            } else {
                // already ok
            }
        } else {
            if(that.firstAdded != null) {
                this.firstAdded = that.firstAdded;
            } else {
                // both are null, leave it like that
            }
        }

        if(this.id == null && that.id != null) {
            this.id = that.id;
        } else if(this.id != null && that.id == null) {
            if(that.records.size() != 0) {
                System.out.printf("how are we supposed to merge these?\r\n");
                return;
            }
            return; // that.id is null and that.records is empty => nothing to merge into this
        } // else ids are the same

        Set<ThroughputInitialRecord> rs1 = this.records == null ? initialiseThroughputInitialRecords() : this.records;
        Set<ThroughputInitialRecord> rs2 = that.records == null ? initialiseThroughputInitialRecords() : that.records;

        this.records = rs1;
        rs1.addAll(rs2);

        this.numOfMerges++;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public String getFirstAggregation() {
        return firstAggregation;
    }

    public void setFirstAggregation(String firstAggregation) {
        this.firstAggregation = firstAggregation;
    }

    public String getLastAggregation() {
        return lastAggregation;
    }

    public void setLastAggregation(String lastAggregation) {
        if(this.firstAggregation == null) {
            this.firstAggregation = lastAggregation;
        }
        this.lastAggregation = lastAggregation;
    }

    public int getNumOfMerges() {
        return numOfMerges;
    }

    public void setNumOfMerges(int numOfMerges) {
        this.numOfMerges = numOfMerges;
    }

    public String getFinalMappingTime() {
        return finalMappingTime;
    }

    public void setFinalMappingTime(String finalMappingTime) {
        this.finalMappingTime = finalMappingTime;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "unable to get string: " + e.getMessage();
        }
    }

}
