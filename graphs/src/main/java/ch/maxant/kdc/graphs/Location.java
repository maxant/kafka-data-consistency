package ch.maxant.kdc.graphs;

public class Location {

    public static enum LocationType {
        CLAIM_LOCATION, PARTNER_ADDRESS
    }

    private String zip;
    private String city;
    private String aggretateId; // ID of the object to which this is related
    private LocationType type;

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setAggretateId(String aggretateId) {
        this.aggretateId = aggretateId;
    }

    public String getAggretateId() {
        return aggretateId;
    }

    public LocationType getType() {
        return type;
    }

    public void setType(LocationType type) {
        this.type = type;
    }
}
