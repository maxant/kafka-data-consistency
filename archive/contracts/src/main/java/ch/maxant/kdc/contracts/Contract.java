/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package ch.maxant.kdc.contracts;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class Contract extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -2405303185465536909L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Contract\",\"namespace\":\"ch.maxant.kdc.contracts\",\"fields\":[{\"name\":\"CONTRACT_ID\",\"type\":\"string\"},{\"name\":\"NETWORK_ID\",\"type\":\"string\"},{\"name\":\"BEGIN\",\"type\":\"string\"},{\"name\":\"END\",\"type\":\"string\"},{\"name\":\"BEGIN_TS\",\"type\":{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}},{\"name\":\"END_TS\",\"type\":{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}},{\"name\":\"CONTRACT_NUMBER\",\"type\":\"int\"},{\"name\":\"SYSTEM\",\"type\":\"string\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();
static {
    MODEL$.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.TimestampMillisConversion());
  }

  private static final BinaryMessageEncoder<Contract> ENCODER =
      new BinaryMessageEncoder<Contract>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<Contract> DECODER =
      new BinaryMessageDecoder<Contract>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<Contract> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<Contract> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<Contract> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<Contract>(MODEL$, SCHEMA$, resolver);
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
  public static Contract fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public java.lang.CharSequence CONTRACT_ID;
  @Deprecated public java.lang.CharSequence NETWORK_ID;
  @Deprecated public java.lang.CharSequence BEGIN;
  @Deprecated public java.lang.CharSequence END;
  @Deprecated public java.time.Instant BEGIN_TS;
  @Deprecated public java.time.Instant END_TS;
  @Deprecated public int CONTRACT_NUMBER;
  @Deprecated public java.lang.CharSequence SYSTEM;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public Contract() {}

  /**
   * All-args constructor.
   * @param CONTRACT_ID The new value for CONTRACT_ID
   * @param NETWORK_ID The new value for NETWORK_ID
   * @param BEGIN The new value for BEGIN
   * @param END The new value for END
   * @param BEGIN_TS The new value for BEGIN_TS
   * @param END_TS The new value for END_TS
   * @param CONTRACT_NUMBER The new value for CONTRACT_NUMBER
   * @param SYSTEM The new value for SYSTEM
   */
  public Contract(java.lang.CharSequence CONTRACT_ID, java.lang.CharSequence NETWORK_ID, java.lang.CharSequence BEGIN, java.lang.CharSequence END, java.time.Instant BEGIN_TS, java.time.Instant END_TS, java.lang.Integer CONTRACT_NUMBER, java.lang.CharSequence SYSTEM) {
    this.CONTRACT_ID = CONTRACT_ID;
    this.NETWORK_ID = NETWORK_ID;
    this.BEGIN = BEGIN;
    this.END = END;
    this.BEGIN_TS = BEGIN_TS.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    this.END_TS = END_TS.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    this.CONTRACT_NUMBER = CONTRACT_NUMBER;
    this.SYSTEM = SYSTEM;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return CONTRACT_ID;
    case 1: return NETWORK_ID;
    case 2: return BEGIN;
    case 3: return END;
    case 4: return BEGIN_TS;
    case 5: return END_TS;
    case 6: return CONTRACT_NUMBER;
    case 7: return SYSTEM;
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
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: CONTRACT_ID = (java.lang.CharSequence)value$; break;
    case 1: NETWORK_ID = (java.lang.CharSequence)value$; break;
    case 2: BEGIN = (java.lang.CharSequence)value$; break;
    case 3: END = (java.lang.CharSequence)value$; break;
    case 4: BEGIN_TS = (java.time.Instant)value$; break;
    case 5: END_TS = (java.time.Instant)value$; break;
    case 6: CONTRACT_NUMBER = (java.lang.Integer)value$; break;
    case 7: SYSTEM = (java.lang.CharSequence)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'CONTRACT_ID' field.
   * @return The value of the 'CONTRACT_ID' field.
   */
  public java.lang.CharSequence getCONTRACTID() {
    return CONTRACT_ID;
  }


  /**
   * Sets the value of the 'CONTRACT_ID' field.
   * @param value the value to set.
   */
  public void setCONTRACTID(java.lang.CharSequence value) {
    this.CONTRACT_ID = value;
  }

  /**
   * Gets the value of the 'NETWORK_ID' field.
   * @return The value of the 'NETWORK_ID' field.
   */
  public java.lang.CharSequence getNETWORKID() {
    return NETWORK_ID;
  }


  /**
   * Sets the value of the 'NETWORK_ID' field.
   * @param value the value to set.
   */
  public void setNETWORKID(java.lang.CharSequence value) {
    this.NETWORK_ID = value;
  }

  /**
   * Gets the value of the 'BEGIN' field.
   * @return The value of the 'BEGIN' field.
   */
  public java.lang.CharSequence getBEGIN() {
    return BEGIN;
  }


  /**
   * Sets the value of the 'BEGIN' field.
   * @param value the value to set.
   */
  public void setBEGIN(java.lang.CharSequence value) {
    this.BEGIN = value;
  }

  /**
   * Gets the value of the 'END' field.
   * @return The value of the 'END' field.
   */
  public java.lang.CharSequence getEND() {
    return END;
  }


  /**
   * Sets the value of the 'END' field.
   * @param value the value to set.
   */
  public void setEND(java.lang.CharSequence value) {
    this.END = value;
  }

  /**
   * Gets the value of the 'BEGIN_TS' field.
   * @return The value of the 'BEGIN_TS' field.
   */
  public java.time.Instant getBEGINTS() {
    return BEGIN_TS;
  }


  /**
   * Sets the value of the 'BEGIN_TS' field.
   * @param value the value to set.
   */
  public void setBEGINTS(java.time.Instant value) {
    this.BEGIN_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
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
   * Gets the value of the 'CONTRACT_NUMBER' field.
   * @return The value of the 'CONTRACT_NUMBER' field.
   */
  public int getCONTRACTNUMBER() {
    return CONTRACT_NUMBER;
  }


  /**
   * Sets the value of the 'CONTRACT_NUMBER' field.
   * @param value the value to set.
   */
  public void setCONTRACTNUMBER(int value) {
    this.CONTRACT_NUMBER = value;
  }

  /**
   * Gets the value of the 'SYSTEM' field.
   * @return The value of the 'SYSTEM' field.
   */
  public java.lang.CharSequence getSYSTEM() {
    return SYSTEM;
  }


  /**
   * Sets the value of the 'SYSTEM' field.
   * @param value the value to set.
   */
  public void setSYSTEM(java.lang.CharSequence value) {
    this.SYSTEM = value;
  }

  /**
   * Creates a new Contract RecordBuilder.
   * @return A new Contract RecordBuilder
   */
  public static ch.maxant.kdc.contracts.Contract.Builder newBuilder() {
    return new ch.maxant.kdc.contracts.Contract.Builder();
  }

  /**
   * Creates a new Contract RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Contract RecordBuilder
   */
  public static ch.maxant.kdc.contracts.Contract.Builder newBuilder(ch.maxant.kdc.contracts.Contract.Builder other) {
    if (other == null) {
      return new ch.maxant.kdc.contracts.Contract.Builder();
    } else {
      return new ch.maxant.kdc.contracts.Contract.Builder(other);
    }
  }

  /**
   * Creates a new Contract RecordBuilder by copying an existing Contract instance.
   * @param other The existing instance to copy.
   * @return A new Contract RecordBuilder
   */
  public static ch.maxant.kdc.contracts.Contract.Builder newBuilder(ch.maxant.kdc.contracts.Contract other) {
    if (other == null) {
      return new ch.maxant.kdc.contracts.Contract.Builder();
    } else {
      return new ch.maxant.kdc.contracts.Contract.Builder(other);
    }
  }

  /**
   * RecordBuilder for Contract instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<Contract>
    implements org.apache.avro.data.RecordBuilder<Contract> {

    private java.lang.CharSequence CONTRACT_ID;
    private java.lang.CharSequence NETWORK_ID;
    private java.lang.CharSequence BEGIN;
    private java.lang.CharSequence END;
    private java.time.Instant BEGIN_TS;
    private java.time.Instant END_TS;
    private int CONTRACT_NUMBER;
    private java.lang.CharSequence SYSTEM;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(ch.maxant.kdc.contracts.Contract.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.CONTRACT_ID)) {
        this.CONTRACT_ID = data().deepCopy(fields()[0].schema(), other.CONTRACT_ID);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.NETWORK_ID)) {
        this.NETWORK_ID = data().deepCopy(fields()[1].schema(), other.NETWORK_ID);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.BEGIN)) {
        this.BEGIN = data().deepCopy(fields()[2].schema(), other.BEGIN);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
      if (isValidValue(fields()[3], other.END)) {
        this.END = data().deepCopy(fields()[3].schema(), other.END);
        fieldSetFlags()[3] = other.fieldSetFlags()[3];
      }
      if (isValidValue(fields()[4], other.BEGIN_TS)) {
        this.BEGIN_TS = data().deepCopy(fields()[4].schema(), other.BEGIN_TS);
        fieldSetFlags()[4] = other.fieldSetFlags()[4];
      }
      if (isValidValue(fields()[5], other.END_TS)) {
        this.END_TS = data().deepCopy(fields()[5].schema(), other.END_TS);
        fieldSetFlags()[5] = other.fieldSetFlags()[5];
      }
      if (isValidValue(fields()[6], other.CONTRACT_NUMBER)) {
        this.CONTRACT_NUMBER = data().deepCopy(fields()[6].schema(), other.CONTRACT_NUMBER);
        fieldSetFlags()[6] = other.fieldSetFlags()[6];
      }
      if (isValidValue(fields()[7], other.SYSTEM)) {
        this.SYSTEM = data().deepCopy(fields()[7].schema(), other.SYSTEM);
        fieldSetFlags()[7] = other.fieldSetFlags()[7];
      }
    }

    /**
     * Creates a Builder by copying an existing Contract instance
     * @param other The existing instance to copy.
     */
    private Builder(ch.maxant.kdc.contracts.Contract other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.CONTRACT_ID)) {
        this.CONTRACT_ID = data().deepCopy(fields()[0].schema(), other.CONTRACT_ID);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.NETWORK_ID)) {
        this.NETWORK_ID = data().deepCopy(fields()[1].schema(), other.NETWORK_ID);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.BEGIN)) {
        this.BEGIN = data().deepCopy(fields()[2].schema(), other.BEGIN);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.END)) {
        this.END = data().deepCopy(fields()[3].schema(), other.END);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.BEGIN_TS)) {
        this.BEGIN_TS = data().deepCopy(fields()[4].schema(), other.BEGIN_TS);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.END_TS)) {
        this.END_TS = data().deepCopy(fields()[5].schema(), other.END_TS);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.CONTRACT_NUMBER)) {
        this.CONTRACT_NUMBER = data().deepCopy(fields()[6].schema(), other.CONTRACT_NUMBER);
        fieldSetFlags()[6] = true;
      }
      if (isValidValue(fields()[7], other.SYSTEM)) {
        this.SYSTEM = data().deepCopy(fields()[7].schema(), other.SYSTEM);
        fieldSetFlags()[7] = true;
      }
    }

    /**
      * Gets the value of the 'CONTRACT_ID' field.
      * @return The value.
      */
    public java.lang.CharSequence getCONTRACTID() {
      return CONTRACT_ID;
    }


    /**
      * Sets the value of the 'CONTRACT_ID' field.
      * @param value The value of 'CONTRACT_ID'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setCONTRACTID(java.lang.CharSequence value) {
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
    public ch.maxant.kdc.contracts.Contract.Builder clearCONTRACTID() {
      CONTRACT_ID = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'NETWORK_ID' field.
      * @return The value.
      */
    public java.lang.CharSequence getNETWORKID() {
      return NETWORK_ID;
    }


    /**
      * Sets the value of the 'NETWORK_ID' field.
      * @param value The value of 'NETWORK_ID'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setNETWORKID(java.lang.CharSequence value) {
      validate(fields()[1], value);
      this.NETWORK_ID = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'NETWORK_ID' field has been set.
      * @return True if the 'NETWORK_ID' field has been set, false otherwise.
      */
    public boolean hasNETWORKID() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'NETWORK_ID' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearNETWORKID() {
      NETWORK_ID = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'BEGIN' field.
      * @return The value.
      */
    public java.lang.CharSequence getBEGIN() {
      return BEGIN;
    }


    /**
      * Sets the value of the 'BEGIN' field.
      * @param value The value of 'BEGIN'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setBEGIN(java.lang.CharSequence value) {
      validate(fields()[2], value);
      this.BEGIN = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'BEGIN' field has been set.
      * @return True if the 'BEGIN' field has been set, false otherwise.
      */
    public boolean hasBEGIN() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'BEGIN' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearBEGIN() {
      BEGIN = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'END' field.
      * @return The value.
      */
    public java.lang.CharSequence getEND() {
      return END;
    }


    /**
      * Sets the value of the 'END' field.
      * @param value The value of 'END'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setEND(java.lang.CharSequence value) {
      validate(fields()[3], value);
      this.END = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'END' field has been set.
      * @return True if the 'END' field has been set, false otherwise.
      */
    public boolean hasEND() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'END' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearEND() {
      END = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'BEGIN_TS' field.
      * @return The value.
      */
    public java.time.Instant getBEGINTS() {
      return BEGIN_TS;
    }


    /**
      * Sets the value of the 'BEGIN_TS' field.
      * @param value The value of 'BEGIN_TS'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setBEGINTS(java.time.Instant value) {
      validate(fields()[4], value);
      this.BEGIN_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'BEGIN_TS' field has been set.
      * @return True if the 'BEGIN_TS' field has been set, false otherwise.
      */
    public boolean hasBEGINTS() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'BEGIN_TS' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearBEGINTS() {
      fieldSetFlags()[4] = false;
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
    public ch.maxant.kdc.contracts.Contract.Builder setENDTS(java.time.Instant value) {
      validate(fields()[5], value);
      this.END_TS = value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      fieldSetFlags()[5] = true;
      return this;
    }

    /**
      * Checks whether the 'END_TS' field has been set.
      * @return True if the 'END_TS' field has been set, false otherwise.
      */
    public boolean hasENDTS() {
      return fieldSetFlags()[5];
    }


    /**
      * Clears the value of the 'END_TS' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearENDTS() {
      fieldSetFlags()[5] = false;
      return this;
    }

    /**
      * Gets the value of the 'CONTRACT_NUMBER' field.
      * @return The value.
      */
    public int getCONTRACTNUMBER() {
      return CONTRACT_NUMBER;
    }


    /**
      * Sets the value of the 'CONTRACT_NUMBER' field.
      * @param value The value of 'CONTRACT_NUMBER'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setCONTRACTNUMBER(int value) {
      validate(fields()[6], value);
      this.CONTRACT_NUMBER = value;
      fieldSetFlags()[6] = true;
      return this;
    }

    /**
      * Checks whether the 'CONTRACT_NUMBER' field has been set.
      * @return True if the 'CONTRACT_NUMBER' field has been set, false otherwise.
      */
    public boolean hasCONTRACTNUMBER() {
      return fieldSetFlags()[6];
    }


    /**
      * Clears the value of the 'CONTRACT_NUMBER' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearCONTRACTNUMBER() {
      fieldSetFlags()[6] = false;
      return this;
    }

    /**
      * Gets the value of the 'SYSTEM' field.
      * @return The value.
      */
    public java.lang.CharSequence getSYSTEM() {
      return SYSTEM;
    }


    /**
      * Sets the value of the 'SYSTEM' field.
      * @param value The value of 'SYSTEM'.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder setSYSTEM(java.lang.CharSequence value) {
      validate(fields()[7], value);
      this.SYSTEM = value;
      fieldSetFlags()[7] = true;
      return this;
    }

    /**
      * Checks whether the 'SYSTEM' field has been set.
      * @return True if the 'SYSTEM' field has been set, false otherwise.
      */
    public boolean hasSYSTEM() {
      return fieldSetFlags()[7];
    }


    /**
      * Clears the value of the 'SYSTEM' field.
      * @return This builder.
      */
    public ch.maxant.kdc.contracts.Contract.Builder clearSYSTEM() {
      SYSTEM = null;
      fieldSetFlags()[7] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Contract build() {
      try {
        Contract record = new Contract();
        record.CONTRACT_ID = fieldSetFlags()[0] ? this.CONTRACT_ID : (java.lang.CharSequence) defaultValue(fields()[0]);
        record.NETWORK_ID = fieldSetFlags()[1] ? this.NETWORK_ID : (java.lang.CharSequence) defaultValue(fields()[1]);
        record.BEGIN = fieldSetFlags()[2] ? this.BEGIN : (java.lang.CharSequence) defaultValue(fields()[2]);
        record.END = fieldSetFlags()[3] ? this.END : (java.lang.CharSequence) defaultValue(fields()[3]);
        record.BEGIN_TS = fieldSetFlags()[4] ? this.BEGIN_TS : (java.time.Instant) defaultValue(fields()[4]);
        record.END_TS = fieldSetFlags()[5] ? this.END_TS : (java.time.Instant) defaultValue(fields()[5]);
        record.CONTRACT_NUMBER = fieldSetFlags()[6] ? this.CONTRACT_NUMBER : (java.lang.Integer) defaultValue(fields()[6]);
        record.SYSTEM = fieldSetFlags()[7] ? this.SYSTEM : (java.lang.CharSequence) defaultValue(fields()[7]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<Contract>
    WRITER$ = (org.apache.avro.io.DatumWriter<Contract>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<Contract>
    READER$ = (org.apache.avro.io.DatumReader<Contract>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}










