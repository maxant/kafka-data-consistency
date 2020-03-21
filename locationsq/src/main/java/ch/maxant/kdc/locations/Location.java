package ch.maxant.kdc.locations;

public class Location {

    public static enum LocationType {
        CLAIM_LOCATION, PARTNER_ADDRESS
    }

    private String zip;
    private String city;
    private String street;
    private String number;
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

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
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

    @Override
    public String toString() {
        return "Location{" +
                "zip='" + zip + '\'' +
                ", city='" + city + '\'' +
                ", street='" + street + '\'' +
                ", number='" + number + '\'' +
                ", aggretateId='" + aggretateId + '\'' +
                ", type=" + type +
                '}';
    }
}
