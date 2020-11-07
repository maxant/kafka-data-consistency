# The Milk Factory

The hottest shake shop in town!

----

## What is The Milk Factory?

The Milk Factory is a venture where we allow customers to create contracts to order customised milk 
products, for example milkshakes. The customer can customise things like fat and sugar content, 
chocolate type, other extras and supplements.

Contracts can be one-off, or subscriptions limited for a given duration, auto-extendable or not.

With a subscription you pay for say 10 per year and you can claim them when you want. This is 
done using a requisition order.

Depending upon the product, there are certain specialities, like our index based products which 
become cheaper / more expensive year on year, depending upon raw product costs.

## Technologie Concept & Architecture

In this project we shall investigate an architecture which is based on something akin to MVC, but
not just for the UI, rather for the entire landscape.

### Architectural Guidelines

- The landscape into microservice based
- Only the UI uses REST, in order to start a process step (this could be replaced by a web socket sending commands 
  to the backend, but we prefer REST+SSE over WebSockets because of browser, infrastructure and security limitations)
- The view uses a controller to execute a process step
- The controller updates the model (e.g. rows in the database)
- The controller emits an event after updating the model
- We prefer orchestration to choreography, i.e. we have one service which delegates to many services, rather than 
  the services delegating to each other. We do this so that we can easily track the process state in the orchestator.
- The event which the controller emits contains the updated model parts, so that the orchestrator can update its
  copy of the model - this is different than MVC in a UI where reading the model is cheap, and allows us to avoid
  expensive REST calls back to microservices in order to re-read data which we just had in memory.
- We use Kafka rather than HTTP for transportation
- We have a concept of commands, where a service demands something be done, and events, where a service publishes
  that something has been done
- The orchestrator can be the browser, but could also be a back end service doing background processing, for example 
  one doing recurring billing
- Services communicate on a single topic, in order to guarantee order, which can be very important during process
  execution
- Decoupled services communicate over their own topics. Services may be decoupled because they have no direct 
  dependencies on other services within the main process.
- Microservices are grouped into applications
- Microservices are cut along functional (business) subjects
- UIs are built as single page applications (SPAs), but are all grouped within the web component, just because this is 
  a small enough project.
  - The sales UI is a SPA with orchestation and state in the browser
  - The recurring billing process is modelled with a backend orchestator which keeps its state in a global KTable, rather
    than say rocksdb or mysql
- Reading via REST is ok, but writing should always occur via Kafka to ensure no writes are ever lost
- All writes should be done idempotently, e.g. by updating rather than blindly inserting, and not failing if 
  something is already deleted

## Applications & Microservices

- contracts
  - contracts themselves including conditions and contract components including products, 
    releases and profiles used to initialise draft contracts
  - additional information
  - discounts
  - pricing
  - billing
- partners
- cases (human workflow)
- output
- requisition orders
- search
- approvals
- notes
- distribution

## Processes

- Sales
  - sell contract
    - create draft
    - modify draft
    - select partner (either new, or existing, must comply with data used to create the draft, e.g. country)
    - offer contract
    - accept offer
    - optional approval
    - organise distribution if this is a one-off contract
  - terminate contract
  - replace contract
- Requisition Order
  - choose component
  - organise distribution
- Distribution
  - ...

## Orchestators

Also known as entry points, process components or UIs.

- Internal
  - Sales
  - Search
  - Partner view
  - Contract view
  - Requisition orders
- External
  - My Account - an overview of a customers contracts and requisition orders
  - Sales - purchasing new products or making changes to existing contracts by replacing them

## Headers, Topcs, Command, Events

- Headers
  - `requestId` for tracking a request through the landscape
  - either `event` or `command` describing what kind of record it is. Events may 
    also contain the `originalCommand` attribute which led to their publication.
- Topics
  - event-bus (generic for all closely-coupled components)
  - cases-commands
  - cases-events
  - errors (for error propagation back to the client)
- Commands (all have the attribute "command" at root level)
  - CREATE_CASE (cases-commands)
  - CREATE_TASK (cases-commands)
  - UPDATE_TASK (cases-commands)
- Events (all have the attribute "event" at root level)
  - ERROR (errors)
  - CASE_CHANGED (cases-events)
  - DRAFT_CREATED (event-bus)
  - UPDATED_PRICES (event-bus)

## Links

- http://blog.maxant.co.uk/pebble/2008/01/02/1199309880000.html
- https://quarkus.io/guides/all-config
- https://lordofthejars.github.io/quarkus-cheat-sheet/
- https://quarkus.io/blog/reactive-messaging-emitter/
- https://dev.mysql.com/doc/refman/8.0/en/json.html
- https://v3.vuejs.org/guide/

## TODO

- ability to change a config in the draft, which recalcs discounts and price
- create create pdf
- accept offer => event to billing

- ok, we want pricing to listen to draft, and we dont want to orchestrate that from the UI. or do we?
- prices: update rather than just insert
- add a task based on a business rule and allow the user to do it
- add action to execute when task is completed or started (ie open UI, or do something)
- addinfo
- discounts
- billing
  - billing is an orchestrator which keeps its model in a global ktable and which uses tombstone records and compaction
- output
- requisition orders
- replace my cors with quarkus cors
- add APM too
- make sales ui sexier
- components diff for warning user after changing to a different product release
- additional info - to hang stuff external to the contract onto components
- billing publishes event to world to inform contract component that the contract is active?
- add a structure rule to components, to ensure there is always a bottle or container in a milk product
- add duration, day in year to bill, billing frequency => make customer pay for first period until next billing period according to config
- publishing billing as command => losely coupled
- add daily billing job
- error handling - https://github.com/cloudstark/quarkus-zalando-problem-extension
- https://quarkus.io/guides/rest-data-panache
- use noArgs for jpa => in pom, but hot deploy doesnt work with mods to entity class

## Blog

- sends a useful model around on the event bus, so that we have guaranteed order
- ui only uses event source for updating - we only update relevant part of ui
- MVC but with microservices
- error handling - timeouts? anything else?
  - error topic - acts as a DLQ, but also one for sending errors back to the client
- kafka interfaces are analagous to rest interfaces.
  - security? check jwt on incoming message?
