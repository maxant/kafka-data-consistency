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

### Architectural Principles & Guidelines

- The landscape into microservice based
- Writing is done using commands over Kafka, apart from the UI which uses REST. This is to increase robustness but also
  to allow services that are under load to fetch work when they are ready, rather than being bombarded with requests.
- Reading is done via REST. In order to increase robustness, we can use self containment (data replication).
- Where the UI uses REST when writing, e.g. in order to start a process step, this could be replaced by a web socket 
  sending commands to the backend, but we prefer REST+SSE over WebSockets because of browser, infrastructure and 
  security limitations
- We attempt to use principles of the MVC pattern (and relations), as the event driven nature seems to be a good fit 
  with the Kafka backbone
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
  - partners, including addresses and partner relationships (to contracts, orders, etc.)
- cases (human workflow, including tasks)
- organisation
  - organisation, security, staff, users, tokens
- output
- requisition orders
- search
- approvals
- notes
- distribution
- organisation including staff and roles

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
- Organisation
  - staff started
  - staff quit
  - security updated
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
- Events (all have the header "event")
  - ERROR (errors)
  - CHANGED_CASE (cases-events)
  - CREATED_DRAFT (event-bus)
  - UPDATED_DRAFT (event-bus)
  - UPDATED_PRICES (event-bus)
  - SECURITY_MODEL (organisation-events)

## Links

- http://blog.maxant.co.uk/pebble/2008/01/02/1199309880000.html
- https://quarkus.io/guides/all-config
- https://lordofthejars.github.io/quarkus-cheat-sheet/
- https://quarkus.io/blog/reactive-messaging-emitter/
- https://dev.mysql.com/doc/refman/8.0/en/json.html
- https://v3.vuejs.org/guide/

## Logs

Worth alerting on:

- EH00* TODO
- SEC-001 no process steps / roles found in security model that are capable of calling $fqMethodName. 
  If this method is called, it will always end in a security exception. To resolve this issue, update 
  the security model or delete the method, if it is no longer required.

## TODO

- pep, pip?, pdp
- security and abac
- put some stuff into internal package in lib - does kotlin have support for this so that you cannot access it outside of the module?
- timestamp - if we attempt to insert but a new version has already been applied, we need to ignore it and log it for alerting. or fail with an error? why not just use optimistic locking. whats on my bit of paper?
- show fat content and other params in portal
- use qute for other SPAs
- move sse and other standard things into vue components
- security: do a view of all methods, and the roles and therefore the users which can run them
- finish security.html
- customer portal
- digitally sign contract
- https://zipkin.io/
- add accepted time to contract as well as by who - altho we are guaranteed its the contract holder, since they have to be, in order to do it
- contracts: execute business rules e.g. if total is higher than customers credit limit, then we need to go thru the approval process
- when showing partner after offering contract, display contracts
- when showing sales rep, use a widget from the partners application
- how do cases and then PARTNER RELATIONSHIPS end up in the client, when they use contractId? ok coz of requestId?
- add accepting and do validation of prices at that point. TRANSPORT still with kafka!
- add the ability to fix errors, so that the user isnt blocked.
- "Must be processed by time X" as a header on messages, after which they are disposed of, since we know 
  the UI will deal with the timeout: in online processes the user will get a timeout. At that stage you 
  don't want to have to say, hey no idea whats still going to happen, rather we want to have a 
  determinate state which can be reloaded and allow the user to restart from there
- add the analagous timeout in the UI
- add sessionId to requestId - errors are propagated back to the session or request?
- UI should show progress of updating, calcing discounts, calcing prices, partner relationships. widget can have knowledge of last one it waits for
- call the contract validation service when we load the draft, and provide problems to the client
- config inheritance - but only certain stuff makes sense, the rest doesnt
- config deviations in cases where the sales team needs to specifically deviate from a normal customisation and it needs to be explicitly mentioned in the contract
- remove support for string messages in the pimp interceptor
- introduce discounts, which adds an extra event => choreography vs orchestration, whats it say about that above?
- create create pdf
- accept offer => event to billing
- calendar to select startTimestamp (rename start to startTimestamp too, coz of conflict in UI)
- ok, we want pricing to listen to draft, and we dont want to orchestrate that from the UI. or do we?
- add a task based on a business rule and allow the user to do it - eg sign off on a big order
- add action to execute when task is completed or started (ie open UI, or do something)
- addinfo
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
- quarkus extension for lib in order to avoid reflection when loading @Secure - a) it wasnt working and b) this is a killer: Local Quarkus extension dependency ch.maxant.kdc.mf:library will not be hot-reloadable

## Further reading

- https://dev.mysql.com/doc/refman/8.0/en/replication-gtids-howto.html
- https://aiven.io/blog/an-introduction-to-apache-cassandra

## Blog

- sends a useful model around on the event bus, so that we have guaranteed order
- ui only uses event source for updating - we only update relevant part of ui
- MVC but with microservices
- error handling - timeouts? anything else?
  - error topic - acts as a DLQ, but also one for sending errors back to the client
- kafka interfaces are analagous to rest interfaces.
  - security? check jwt on incoming message?
- publish product limits with rest versus we publish them in the initial draft. they are set inside the component definitions
- process step validation of consistency: use a coordinated timestamp to ensure all records are consistent. lets call it a consistencyTimestamp
- Waiting room for retryable errors: topics where consumer delays. Works because everything in the topic needs to wait 
  longer than the first one. 10 sec topic has consumers that wait that long and their consumer config allows that. Send
  to waiting room contains a header so it knows where to send it back to
- Retryable vs non retryable. Unique constraint, validation, illegal argument are non retryable. Anything else?
- validation of some process steps is done with online rest calls so that we can ensure that we 
  dont allow inconsistent cases to be processed beyond a certain point. the user has to fix any 
  problems before we allow them to continue
- syncTimestamp vs having a substate in the contract which gets updated if there are errors - its analagous
  to always calling services with OUR key and not relying on getting their key, which we may not get if the response fails to arrive
- when creating partner relationships, we send as much info in the command as possible, so that eg the selection of the sales rep can be done in the right place, namely the partner service
- publishing security data when it changes, or when org starts up
  - fetching security data during startup or lazily if that fails, so that we can check jwts based on processes

### the five tenets of global data consistency

- async communication for writing data
  - realised with kafka
  - pull principle, so that the system isnt overloaded
- use automated retry for non-business errors
  - realised with the waiting room with a suitable back off strategy
  - which allows for a self healing system
- error propagation back to the originator
  - realised with the requestId and web component which filters data and returns it to the right browser
- timeouts and "must be processed by"
  - and the ability to reload and fix problems, e.g. recalculating discounts and prices
- sync timestamp in order to be able to determine when data is not globally consistent
  - eg upstream we committed, but downstream we ran into a problem (business or technical)
  - validation before important process steps: partners may already exist beforehand and they are 
    loosely coupled to the sales system, so it doesnt make sense to use a syncTimestamp for them => just check existance 

- https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- https://www.youtube.com/watch?v=CZ3wIuvmHeM
  - why microservices?
    - an abstraction
    - easier to understand where problem is because a service is smaller than a monolith
    - one monolithic db goes down, it all goes down. costly to run such a large db
    - separation of concerns
      - modularity + encapsulation
    - scalability: horizontal + workload paritioning
    - virtualisation + elasticity (automated ops + on demand provisioning)
    - for netflix, a microservice includes the client lib+cache
    - rest call potential problems: network latency, congestions, logical failure, scaling failure, timeouts, human error/deployment
      - handling timeouts, fallbacks, fail fast, feedback
      - FIT fault injection testing
    - critical microservices => the path which MUST run for the most basic availability. ensure those services fail less
    - netflix builds client libs to avoid duplicated code. dodgy, since teams operate code they dont own, and a bug will cause failure in many services
    - api gateway, like their central orchestator?
    - oracle doesnt offer eventual consistency, cassandra however does, like kafka - so long as one node is running, you can continue, albeit at a risk
      - hmm... entirely true? its clustered after all
    - dont let offline run on operations db, but on replications. partition clients?
    - client has encrypted state which is sent with requests and can be used in callback. why encrypted and why only for fallback?
    - 

## Running tips

    export MAVEN_OPTS="-Xmx200m"
    mvn quarkus:dev -Ddebug=5006

    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-11.0.9.11-0.el7_9.x86_64
    mvn --version

## Infrastructure

    CREATE USER 'mfcontracts'@'%' IDENTIFIED BY 'secret';
    CREATE DATABASE mfcontracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GRANT ALL PRIVILEGES ON mfcontracts.* TO mfcontracts@'%' IDENTIFIED BY 'the_password';
