package ch.maxant.kdc.mf.contracts.entity

enum class Status {

    /** TMF has created an offer, but the customer doesnt know about it yet */
    DRAFT,

    /** TMF has created an offer, which has been given to the customer for them to review it */
    OFFERED,

    /** The customer has accepted the offer. */
    ACCEPTED,

    /** The customer has accepted the offer but it still needs approval from TMF. */
    AWAITING_APPROVAL,

    /** TMF has approved the accepted offer and it will shortly be running */
    APPROVED,

    /** TMF has a running contract with the customer. Their subscription may not yet have started! */
    RUNNING,

    /** The contract has been cancelled as though it never existed. */
    CANCELLED,

    /** The contract has run its length. */
    EXPIRED,

    /** The contract was terminated prematurely. */
    TERMINATED
}
