/**
 * created by ant to see what happens if we select less - tolerant reader pattern!
 */
package ch.maxant.kdc.contracts;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

public class ContractLite extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -2405303185465536909L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Contract\",\"namespace\":\"ch.maxant.kdc.contracts\",\"fields\":[{\"name\":\"CONTRACT_ID\",\"type\":\"string\"},{\"name\":\"BEGIN\",\"type\":\"string\"},{\"name\":\"END_TS\",\"type\":{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();
  static {
    MODEL$.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.TimestampMillisConversion());
  }

  private static final BinaryMessageEncoder<ContractLite> ENCODER =
      new BinaryMessageEncoder<ContractLite>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<ContractLite> DECODER =
      new BinaryMessageDecoder<ContractLite>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<ContractLite> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<ContractLite> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<ContractLite> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<ContractLite>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this Contract to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a Contract from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a Contract instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static ContractLite fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public CharSequence CONTRACT_ID;
  @Deprecated public CharSequence BEGIN;
  @Deprecated public java.time.Instant END_TS;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public ContractLite() {}

  /**
   * All-args constructor.
   * @param CONTRACT_ID The new value for CONTRACT_ID
   * @param BEGIN The new value for BEGIN
   * @param END_TS The new value for END_TS
   */
  public ContractLite(CharSequence CONTRACT_ID, CharSequence BEGIN, java.time.Instant END_TS) {
    this.CONTRACT_ID = CONTRACT_ID;
    this.BEGIN = BEGIN;
    this.END_TS = END_TS.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  public SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public Object get(int field$) {
    switch (field$) {
    case 0: return CONTRACT_ID;
    case 1: return BEGIN;
    case 2: return END_TS;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  private static final org.apache.avro.Conversion<?>[] conversions =
      new org.apache.avro.Conversion<?>[] {
      null,
      null,
      null,
      null,
      new org.apache.avro.data.TimeConversions.TimestampMillisConversion(),
      new org.apache.avro.data.TimeConversions.TimestampMillisConversion(),
      null,
      null,
      null
  };

  @Override
  public org.apache.avro.Conversion<?> getConversion(int field) {
    return conversions[field];
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, Object value$) {
    switch (field$) {
    case 0: CONTRACT_ID = (CharSequence)value$; break;
    case 1: BEGIN = (CharSequence)value$; break;
    case 2: END_TS = (java.time.Instant)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'CONTRACT_ID' field.
   * @return The value of the 'CONTRACT_ID' field.
   */
  public CharSequence getCONTRACTID() {
    return CONTRACT_ID;
  }


  /**
   * Sets the value of the 'CONTRACT_ID' field.
   * @param value the value to set.
   */
  public void setCONTRACTID(CharSequence value) {
    this.CONTRACT_ID = value;
  }

  /**
   * Gets the value of the 'BEGIN' field.
   * @return The value of the 'BEGIN' field.
   */
  public CharSequence getBEGIN() {
    return BEGIN;
  }


  /**
   * Sets the value of the 'BEGIN' field.
   * @param value the value to set.
   */
  public void setBEGIN(CharSequence value) {
    this.BEGIN = value;
  }

  /**
   * Gets the value of the 'END_TS' field.
   * @return The value of the 'END_TS' field.
   */
  public java.time.Instant getENDTS() {
    return END_TS;
  }


  /**
   * Sets the value of the 'END_TS' field.
   * @param value the value to set.
   */
  public void setENDTS(java.time.Instant value) {
    this.END_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  /**
   * Creates a new Contract RecordBuilder.
   * @return A new Contract RecordBuilder
   */
  public static ContractLite.Builder newBuilder() {
    return new ContractLite.Builder();
  }

  /**
   * Creates a new Contract RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Contract RecordBuilder
   */
  public static ContractLite.Builder newBuilder(ContractLite.Builder other) {
    if (other == null) {
      return new ContractLite.Builder();
    } else {
      return new ContractLite.Builder(other);
    }
  }

  /**
   * Creates a new Contract RecordBuilder by copying an existing Contract instance.
   * @param other The existing instance to copy.
   * @return A new Contract RecordBuilder
   */
  public static ContractLite.Builder newBuilder(ContractLite other) {
    if (other == null) {
      return new ContractLite.Builder();
    } else {
      return new ContractLite.Builder(other);
    }
  }

  /**
   * RecordBuilder for Contract instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<ContractLite>
    implements org.apache.avro.data.RecordBuilder<ContractLite> {

    private CharSequence CONTRACT_ID;
    private CharSequence BEGIN;
    private java.time.Instant END_TS;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(ContractLite.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.CONTRACT_ID)) {
        this.CONTRACT_ID = data().deepCopy(fields()[0].schema(), other.CONTRACT_ID);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.BEGIN)) {
        this.BEGIN = data().deepCopy(fields()[1].schema(), other.BEGIN);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.END_TS)) {
        this.END_TS = data().deepCopy(fields()[2].schema(), other.END_TS);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
    }

    /**
     * Creates a Builder by copying an existing Contract instance
     * @param other The existing instance to copy.
     */
    private Builder(ContractLite other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.CONTRACT_ID)) {
        this.CONTRACT_ID = data().deepCopy(fields()[0].schema(), other.CONTRACT_ID);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.BEGIN)) {
        this.BEGIN = data().deepCopy(fields()[1].schema(), other.BEGIN);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.END_TS)) {
        this.END_TS = data().deepCopy(fields()[2].schema(), other.END_TS);
        fieldSetFlags()[2] = true;
      }
    }

    /**
      * Gets the value of the 'CONTRACT_ID' field.
      * @return The value.
      */
    public CharSequence getCONTRACTID() {
      return CONTRACT_ID;
    }


    /**
      * Sets the value of the 'CONTRACT_ID' field.
      * @param value The value of 'CONTRACT_ID'.
      * @return This builder.
      */
    public ContractLite.Builder setCONTRACTID(CharSequence value) {
      validate(fields()[0], value);
      this.CONTRACT_ID = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'CONTRACT_ID' field has been set.
      * @return True if the 'CONTRACT_ID' field has been set, false otherwise.
      */
    public boolean hasCONTRACTID() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'CONTRACT_ID' field.
      * @return This builder.
      */
    public ContractLite.Builder clearCONTRACTID() {
      CONTRACT_ID = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'BEGIN' field.
      * @return The value.
      */
    public CharSequence getBEGIN() {
      return BEGIN;
    }


    /**
      * Sets the value of the 'BEGIN' field.
      * @param value The value of 'BEGIN'.
      * @return This builder.
      */
    public ContractLite.Builder setBEGIN(CharSequence value) {
      validate(fields()[1], value);
      this.BEGIN = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'BEGIN' field has been set.
      * @return True if the 'BEGIN' field has been set, false otherwise.
      */
    public boolean hasBEGIN() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'BEGIN' field.
      * @return This builder.
      */
    public ContractLite.Builder clearBEGIN() {
      BEGIN = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'END_TS' field.
      * @return The value.
      */
    public java.time.Instant getENDTS() {
      return END_TS;
    }


    /**
      * Sets the value of the 'END_TS' field.
      * @param value The value of 'END_TS'.
      * @return This builder.
      */
    public ContractLite.Builder setENDTS(java.time.Instant value) {
      validate(fields()[2], value);
      this.END_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'END_TS' field has been set.
      * @return True if the 'END_TS' field has been set, false otherwise.
      */
    public boolean hasENDTS() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'END_TS' field.
      * @return This builder.
      */
    public ContractLite.Builder clearENDTS() {
      fieldSetFlags()[2] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContractLite build() {
      try {
        ContractLite record = new ContractLite();
        record.CONTRACT_ID = fieldSetFlags()[0] ? this.CONTRACT_ID : (CharSequence) defaultValue(fields()[0]);
        record.BEGIN = fieldSetFlags()[1] ? this.BEGIN : (CharSequence) defaultValue(fields()[1]);
        record.END_TS = fieldSetFlags()[2] ? this.END_TS : (java.time.Instant) defaultValue(fields()[2]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<ContractLite>
    WRITER$ = (org.apache.avro.io.DatumWriter<ContractLite>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<ContractLite>
    READER$ = (org.apache.avro.io.DatumReader<ContractLite>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}










