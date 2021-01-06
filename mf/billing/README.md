# The Milk Factory :: billing

A component for dealing with billing processes.

## Initial Billing

No different from recurring billing, other than it's run because a contract is created, rather than
because it's scheduled. The trigger event includes the contract id so that the selection runs faster.

## Recurring Billing

- A schedule creates an event that decides what period needs to be billed for, e.g. "today", if billing
  periods were one day long.
- The selection process chooses contracts that are running and who are not yet completely billed
  for the given period. For testing, it is possible that we delete existing bills!
  - This process must also select anything which was missed in previous billing runs.
  - For each candidate contract we calculate the period(s) that need billing, ie. from and to.
  - A billing period, e.g. a month, has a fixed price, and once billed is not changed (unless it's deleted
    for testing purposes)
- The candidates are split into groups, since we cannot do updates for hundreds of thousands of contracts
  in a single transaction. Equally, it won't perform well if we update each contract singularly.
  - Group size is to be determined using load tests - or should the system use feedback to optimise it? :-)
- Groups are sent to pricing, to ensure that a price has been calculated for the given billing period(s)
- Bills are created
- Output is sent to customers, demanding payment.
- Progress is tracked using a Kafka global KTable
- Progress can be monitored using SSE
  - If a client connects after billing has started, it receives all the events that occurred prior to 
    the connection
- Market prices dictate that the price can change per day.
  - Theoretically:
    - if pricing is done in arrears, then the prices are based on actual market prices
    - if pricing is done in advance, then the prices are based on predictions
      - For this reason, there are surcharges to cover potential losses by the Milk Factory
  - Practically: prices are random for this poc :-)
- Billing is always done in advance

## Data Model

- Billing Definition
  - a Kotlin class
  - Represents the time period for which the price is constant
  - Can be chosen per contract
  - Fields
    - Base Billing Periodicity - the amount of time for which a price remains constant
      - Yearly, Monthly, Daily
      - used by pricing to calculate the price for the given period. Basically, pricing contains rows of 
        this length (not initally true, where the price is calculated for the contract length, but it's 
        updated as soon as billing occurs)
    - Chosen Billing Periodicity - you can pay more or less frequently than the period for which the price is constant
      - Yearly, Monthly, Daily
      - used by billing to calculate the actual bill
    - Surcharge of chosen billing periodicity, to cover the costs of TMF prediction risks
    - Reference Day: Int - the day on which the period starts, can be a day in the year, month, week or 0 for daily
    - Notes:
      - if base > chosen, then bills are pro rata (a year has 360 days; a month has 30 days; 12 months in a year)
      - if base = chosen, then bills are 1:1
      - if base < chosen, then bills contain a list of the price per base period
      - the bill can always contain all the info: whats the base price and for which period; for which period is this bill, etc.
- Billing Mapping
  - an entity
  - Represents the mapping from the contract to the chosen billing definition
  - Fields:
      - id
      - contractId
      - definitionId
- Bill Chosen (this is what the customer pays)
  - Entity
  - Represents a single bill
  - Fields:
      - id
      - contractId
      - chosen period from
      - chosen period to
      - chosen period price - this is what the customer must pay!
      - surcharge
      - total price
      - state
        - BILLED, REMINDED, NEVER_PAID (customer refused, see other processes), PAID
- Bill Base (this is the basis of the calculation)
  - Entity
  - Represents the base price(s)
  - Fields:
      - id
      - base period from
      - base period to
      - base period price - this is just the basis of the calculation
- Bill base to chosen
  - Entity (join table)
  - Represents the n:m relationship between chosen and base periods
    - 1:1: base bill periodicity equals chosen bill periodicity, e.g. both "monthly"
    - 1:m: e.g. a price fixed for a year is paid 12 times
    - n:1: e.g. a price that changes monthly is paid once per year
    - n:m: doesn't exist from the above perspective, although 1:m and n:1 are actually n:m when you think that you keep paying them, year on year
  - Fields:
      - id
      - billChosenId
      - billBaseId

## Running in dev mode

From inside the billing folder, so that changes to the library are also hot deployed:

```
mvn quarkus:dev
```

## Packaging and running the application

`mvn package` produces the `billing-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/billing-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/billing-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://billing:8086/swagger-ui

## TODO

