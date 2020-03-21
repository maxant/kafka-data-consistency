package ch.maxant.kdc.partners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ThroughputAggregateRecord {

    private String id;
    private List<ThroughputInitialRecord> records = new ArrayList<>();
    private String firstProcessed = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private String lastProcessed = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    public void addRecord(ThroughputInitialRecord r) {
        records.add(r);
        lastProcessed = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public String getId() {
        return id;
    }

    public List<ThroughputInitialRecord> getRecords() {
        return records;
    }

    public void setId(String id) {
        if(this.id != null) {
            if(!this.id.equals(id)) throw new RuntimeException("keys not same");
        } else {
            this.id = id;
        }
    }

    public String getEarliestCreated() {
        return records.stream().map(ThroughputInitialRecord::getStart).sorted().findFirst().orElse(null);
    }

    public String getLatestCreated() {
        return records.stream().map(ThroughputInitialRecord::getStart).sorted(Comparator.reverseOrder()).findFirst().orElse(null);
    }

    public String getFirstProcessed() {
        return firstProcessed;
    }

    public String getLastProcessed() {
        return lastProcessed;
    }

    public int getNumRecordsProcessed() {
        return records.size();
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
