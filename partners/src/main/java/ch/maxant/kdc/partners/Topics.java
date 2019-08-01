package ch.maxant.kdc.partners;

public interface Topics {

    /** contains raw data of partners that have just been created */
    String PARTNER_CREATED_EVENT = "partner-created-event";

    /** contains raw data of partners but anonymised */
    String PARTNER_CREATED_ANONYMOUS = "partner-created-anonymous";

    /** a topic containing just the german speakers, their last names are anonymised, and their age has been added to the record */
    String PARTNER_CREATED_GERMAN_SPEAKING_ANONYMOUS_WITH_AGE = "partner-created-german-speaking-anonymous-with-age";

    /** a topic used to feed a global ktable */
    String PARTNER_CREATED_GERMAN_SPEAKING_GLOBAL_COUNT = "partner-created-german-speaking-global-count";
}
