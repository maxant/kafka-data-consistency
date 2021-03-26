/**
 * created by ant to see what happens if we select less - tolerant reader pattern!
 */
package ch.maxant.kdc.contracts;

public class Contract extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {

    public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Contract\",\"namespace\":\"ch.maxant.kdc.contracts\",\"fields\":[{\"name\":\"CONTRACT_ID\",\"type\":\"string\"},{\"name\":\"BEGIN\",\"type\":\"string\"},{\"name\":\"END_TS\",\"type\":{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}}]}");

    private CharSequence CONTRACT_ID;
    private CharSequence BEGIN;
    private java.time.Instant END_TS;

    public Contract() {
    }

    public Contract(CharSequence CONTRACT_ID, CharSequence BEGIN, java.time.Instant END_TS) {
        this.CONTRACT_ID = CONTRACT_ID;
        this.BEGIN = BEGIN;
        this.END_TS = END_TS.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    public org.apache.avro.Schema getSchema() {
        return SCHEMA$;
    }

    // Used by DatumWriter.  Applications should not call.
    public Object get(int field$) {
        switch (field$) {
            case 0:
                return CONTRACT_ID;
            case 1:
                return BEGIN;
            case 2:
                return END_TS;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    private static final org.apache.avro.Conversion<?>[] conversions =
            new org.apache.avro.Conversion<?>[]{
                    null,
                    null,
                    new org.apache.avro.data.TimeConversions.TimestampMillisConversion(),
            };

    @Override
    public org.apache.avro.Conversion<?> getConversion(int field) {
        return conversions[field];
    }

    // Used by DatumReader.  Applications should not call.
    @SuppressWarnings(value = "unchecked")
    public void put(int field$, Object value$) {
        switch (field$) {
            case 0:
                CONTRACT_ID = (CharSequence) value$;
                break;
            case 1:
                BEGIN = (CharSequence) value$;
                break;
            case 2:
                END_TS = (java.time.Instant) value$;
                break;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    public CharSequence getCONTRACTID() {
        return CONTRACT_ID;
    }

    public void setCONTRACTID(CharSequence value) {
        this.CONTRACT_ID = value;
    }

    public CharSequence getBEGIN() {
        return BEGIN;
    }

    public void setBEGIN(CharSequence value) {
        this.BEGIN = value;
    }

    public java.time.Instant getENDTS() {
        return END_TS;
    }

    public void setENDTS(java.time.Instant value) {
        this.END_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

}










