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
- Billing
  - initial, as well as recurring
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
  - Billing?
- External
  - My Account - an overview of a customers contracts and requisition orders
  - Sales - purchasing new products or making changes to existing contracts by replacing them

## Billing

    select CONTRACT_ID, STARTTIME, PRICE, TAX 
    from mfpricing.T_PRICES WHERE COMPONENT_ID IN (
        select component.ID 
        from mfcontracts.T_COMPONENTS component, mfcontracts.T_CONTRACTS contract 
        where contract.STATE = 'RUNNING' 
          AND component.CONTRACT_ID = contract.ID
          AND component.PARENT_ID is null
          AND contract.STARTTIME = '2021-12-17 00:00:00.000'
    )
    limit 10;

    select price.CONTRACT_ID, price.STARTTIME, price.PRICE, price.TAX 
    from mfpricing.T_PRICES price, mfcontracts.T_COMPONENTS component, mfcontracts.T_CONTRACTS contract 
    where price.COMPONENT_ID = component.ID
      AND component.CONTRACT_ID = contract.ID 
      AND contract.STATE = 'RUNNING' 
      AND component.PARENT_ID is null
      AND contract.STARTTIME = '2021-12-17 00:00:00.000'
    limit 10;

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p
    docker run -it --rm mysql mysql -h 192.168.1.215 --port 30300 -u root -p
    SHOW VARIABLES LIKE "general_log%";
    +------------------+---------------------------------+
    | Variable_name    | Value                           |
    +------------------+---------------------------------+
    | general_log      | OFF                             |
    | general_log_file | /var/lib/mysql/a1a9fa654596.log |
    +------------------+---------------------------------+
    2 rows in set (2.29 sec)
    
    SET GLOBAL general_log = 'ON';

    # using the filename above =>
    docker-compose -f dc-base.yml exec kdc-mysql tail -f /var/lib/mysql/a1a9fa654596.log

    # afterwards:
    SET GLOBAL general_log = 'OFF';


## Headers, Topcs, Command, Events

- Headers
  - `requestId` for tracking a request through the landscape
  - either `event` or `command` describing what kind of record it is. Events may 
    also contain the `originalCommand` attribute which led to their publication.
- Topics
  - contracts-event-bus (generic for all closely-coupled components)
  - contracts-internal-es (for async load into ES)
  - cases-commands
  - cases-events
  - errors (for error propagation back to the client)
  - organisation-events (events about changes to the organisation)
  - partners-commands
  - partners-events
  - partners-internal-es (for async load into ES)
  - waitingroom01 (a place for records to do wait for 1 second, before retrying)
  - waitingroom10 (a place for records to do wait for 10 seconds, before retrying)
- Commands (all have the attribute "command" at root level)
  - CREATE_CASE (cases-commands)
  - CREATE_TASK (cases-commands)
  - UPDATE_TASK (cases-commands)
- Events (all have the header "event")
  - ERROR (errors)
  - CHANGED_CASE (cases-events)
  - CREATED_DRAFT (contracts-event-bus)
  - UPDATED_DRAFT (contracts-event-bus)
  - UPDATED_PRICES (contracts-event-bus)
  - SECURITY_MODEL (organisation-events)
  - PARTNER_CHANGED? (partner-events)

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
- SEC-002 TODO
- SEC-003 TODO
- CONES001 TODO
- PARES001 TODO
- KAF001 If this occurs, then something is no longer working with request scoped state management
- KAF002 something failed in the consumer that needs to be investigated. probably at startup rather than during polling.

## Useful log searches

    ("*Exception*" or "ERROR" or "WARN") AND NOT ensureUserIsContractHolder AND NOT EH004a AND NOT CONES001

## Cypress Testing

https://docs.cypress.io/guides/getting-started/installing-cypress.html

    npx cypress open
    npx cypress run --spec "cypress/integration/partner_spec.js" --headless --browser chrome

## TODO

- discounts can be added to any component in the tree, as a child component! still not sure to which component these belong
- fix external sales
- billing
  - accept offer => event to billing
  - billing is an orchestrator which keeps its model in a global ktable and which uses tombstone records and compaction
  - billing publishes event to world to inform contract component that the contract is active?
  - add duration, day in year to bill, billing frequency => make customer pay for first period until next billing period according to config
  - add daily billing job
    - group (and unfold on errors)
    - update prices (due to market price on certain components)
    - calculate bill for given period (regardless of what already exists)
    - send output
    - progress tracking
  - send event back to UI so that the customer can pay directly for the first billed period
- allow changing the quantity in the offer, so you can get under the approval threshold
  - somethings not quite right - the price doesnt account for the quantity in the box!
- display existing draft so that you can continue working from there
- change from external to internal, if you arent sure and need a consultation
- add createdAt/By to all entities and order partner relationships by that and load only the latest ones
- sales uis should use a state object which is appended to when events arrive, and it is used to determine what buttons etc become available
  - actually, thats kinda cool, coz cypress just has to wait til it can click the buttons?
- timestamp - if we attempt to insert but a new version has already been applied, we need to ignore it and log it for alerting. or fail with an error? why not just use optimistic locking. whats on my bit of paper?
- sessionId
- web register for session, request, contract, etc. not just requestId
- pep, pip?, pdp
- put some stuff into internal package in lib - does kotlin have support for this so that you cannot access it outside of the module?
- show fat content and other params in portal
- use qute for other SPAs: bookmarks
- move sse and other standard things into vue components
- security: do a view of all methods, and the roles and therefore the users which can run them
- finish security.html
- https://zipkin.io/
- how do cases and then PARTNER RELATIONSHIPS end up in the client, when they use contractId? ok coz of requestId?
- add accepting and do validation of prices at that point. TRANSPORT still with kafka!
- add the ability to fix errors, so that the user isnt blocked.
- call the contract validation service when we load the draft, and provide problems to the client
- "Must be processed by time X" as a header on messages, after which they are disposed of, since we know 
  the UI will deal with the timeout: in online processes the user will get a timeout. At that stage you 
  don't want to have to say, hey no idea whats still going to happen, rather we want to have a 
  determinate state which can be reloaded and allow the user to restart from there
- add the analagous timeout in the UI
- add sessionId to requestId - errors are propagated back to the session or request?
- after sales
  - terminate
  - cancel
  - etc
- UI should show progress of updating, calcing discounts, calcing prices, partner relationships. widget can have knowledge of last one it waits for
- component config inheritance - but only certain stuff makes sense, the rest doesnt
- component config deviations in cases where the sales team needs to specifically deviate from a normal customisation and it needs to be explicitly mentioned in the contract
- remove support for string messages in the pimp interceptor
- introduce discounts, which adds an extra event => choreography vs orchestration, whats it say about that above?
  - easier to just add an age based discount directly to the root price
  - saying that, we arleady have a market price for milk, so that means you always need to update the price, before going on to bill for the next period
- calendar to select startTimestamp (rename start to startTimestamp too, coz of conflict in UI)
- ok, we want pricing to listen to draft, and we dont want to orchestrate that from the UI. or do we?
- add action to execute when task is completed or started (ie open UI, or do something)
- create contract pdf?
- output
- requisition orders
- replace my cors with quarkus cors? can it do everything i need?
- components diff for warning user after changing to a different product release
- additional info - to hang stuff external to the contract onto components
- add a structure rule to components, to ensure there is always a bottle or container in a milk product
- error handling - https://github.com/cloudstark/quarkus-zalando-problem-extension
- https://quarkus.io/guides/rest-data-panache
- make sales ui sexier
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

## Create Elastic Search Indices

For nested docs, see this first: https://www.elastic.co/guide/en/elasticsearch/reference/current/properties.html

Kibana searches:

    components:{ componentDefinitionId: CardboardBox }
    components:{ configs:{ name: FAT_CONTENT AND value: 1.8 } }
    start:[2020-01-01 TO 2020-12-31]

Check existing:

    curl -X GET "kdc.elasticsearch.maxant.ch/contracts"
    curl -X GET "kdc.elasticsearch.maxant.ch/partners"

Delete existing:

    curl -X DELETE "kdc.elasticsearch.maxant.ch/contracts"
    curl -X DELETE "kdc.elasticsearch.maxant.ch/partners"

Create new:

    curl -X PUT "kdc.elasticsearch.maxant.ch/contracts" -H 'Content-Type: application/json' -d'
    {
        "settings" : {
            "index" : {
                "number_of_shards" : 1,
                "number_of_replicas" : 1
            }
        },
        "mappings" : {
            "properties": {
                "partnerId": { "type": "keyword" },
                "totalPrice": { "type": "double" },
                "start": { "type": "date", "format": "date_hour_minute_second" },
                "end": { "type": "date", "format": "date_hour_minute_second" },
                "state": { "type": "text" },
                "metainfo": { "type": "text" }
            }
        }
    }
    '

    curl -X PUT "kdc.elasticsearch.maxant.ch/partners" -H 'Content-Type: application/json' -d'
    {
        "settings" : {
            "index" : {
                "number_of_shards" : 1,
                "number_of_replicas" : 1
            },
            "analysis": {
                "filter": {
                    "english_stop": {
                        "type":       "stop",
                        "stopwords":  "_english_"
                    },
                    "english_stemmer": {
                        "type":       "stemmer",
                        "language":   "english"
                    },
                    "german_stop": {
                        "type":       "stop",
                        "stopwords":  "_german_"
                    },
                    "german_stemmer": {
                        "type":       "stemmer",
                        "language":   "light_german"
                    },
                    "french_elision": {
                        "type":         "elision",
                        "articles_case": true,
                        "articles": [
                          "l", "m", "t", "qu", "n", "s",
                          "j", "d", "c", "jusqu", "quoiqu",
                          "lorsqu", "puisqu"
                        ]
                    },
                    "french_stop": {
                        "type":       "stop",
                        "stopwords":  "_french_"
                    },
                    "french_stemmer": {
                        "type":       "stemmer",
                        "language":   "light_french"
                    }
                },
                "analyzer": {
                    "ants_analyzer": {
                        "tokenizer": "standard",
                        "filter": [
                            "lowercase",
                            "english_stop",
                            "english_stemmer",
                            "german_stop",
                            "german_normalization",
                            "german_stemmer",
                            "french_elision",
                            "french_stop",
                            "french_stemmer"
                        ]
                    }
                }
            }
        },
        "mappings" : {
            "properties": {
                "partnerId": { "type": "keyword" },
                "firstName": { "type": "text", "analyzer": "ants_analyzer" },
                "lastName": { "type": "text", "analyzer": "ants_analyzer" },
                "dob": { "type": "date", "format": "strict_date" },
                "email": { "type": "text", "analyzer": "ants_analyzer" },
                "phone": { "type": "text", "analyzer": "ants_analyzer" },
                "type": { "type": "text", "analyzer": "ants_analyzer" },
                "addresses": {
                    "type": "nested",
                    "properties": {
                        "street": { "type": "text", "analyzer": "ants_analyzer" },
                        "houseNumber": { "type": "text" },
                        "postcode": { "type": "text" },
                        "city": { "type": "text", "analyzer": "ants_analyzer" },
                        "state": { "type": "text", "analyzer": "ants_analyzer" },
                        "country": { "type": "text", "analyzer": "ants_analyzer" },
                        "type": { "type": "text" }
                    }
                }
            }
        }
    }
    '


## Running tips

    export MAVEN_OPTS="-Xmx400m"
    mvn quarkus:dev -Ddebug=5006

    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-11.0.9.11-0.el7_9.x86_64
    mvn --version

## Infrastructure

    CREATE USER 'mf'@'%' IDENTIFIED BY 'secret';
    CREATE DATABASE mfcontracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GRANT ALL PRIVILEGES ON mfcontracts.* TO mf@'%' IDENTIFIED BY 'the_password';
    CREATE DATABASE mfpricing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GRANT ALL PRIVILEGES ON mfpricing.* TO mf@'%' IDENTIFIED BY 'the_password';
    CREATE DATABASE mfcases CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GRANT ALL PRIVILEGES ON mfcases.* TO mf@'%' IDENTIFIED BY 'the_password';
    CREATE DATABASE mfpartners CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GRANT ALL PRIVILEGES ON mfpartners.* TO mf@'%' IDENTIFIED BY 'the_password';

## Bugs

2021-01-03 15:26:32,356 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] >>> APP.CREATED ch.maxant.kdc.mf.library.Other@4c4e20cd
2021-01-03 15:26:32,358 INFO  [ch.max.kdc.mf.lib.State] (thread-1) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@383485ef
2021-01-03 15:26:32,359 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] set state to 1
2021-01-03 15:26:32,358 INFO  [ch.max.kdc.mf.lib.State] (thread-2) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@7220eab1
2021-01-03 15:26:32,361 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] set state to 2
2021-01-03 15:26:32,367 INFO  [ch.max.kdc.mf.lib.State] (Quarkus Main Thread) [request-id: c: e:] >>> STATE.DESTROYING ch.maxant.kdc.mf.library.State@7220eab1
2021-01-03 15:26:32,899 INFO  [ch.max.kdc.mf.lib.State] (thread-1) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@72dd4f0f
2021-01-03 15:26:32,901 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] doing it, state is asdf

2021-01-03 15:30:00,609 INFO  [ch.max.kdc.mf.lib.Other] (Quarkus Main Thread) [request-id: c: e:] >>> APP.DESTROYING ch.maxant.kdc.mf.library.Other@4c4e20cd

2021-01-03 15:30:03,386 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] >>> APP.CREATED ch.maxant.kdc.mf.library.Other@331de08a
2021-01-03 15:30:03,388 INFO  [ch.max.kdc.mf.lib.State] (thread-1) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@77643847
2021-01-03 15:30:03,388 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] set state to 1
2021-01-03 15:30:03,389 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] set state to 2
2021-01-03 15:30:03,647 INFO  [ch.max.kdc.mf.lib.State] (Quarkus Main Thread) [request-id: c: e:] >>> STATE.DESTROYING ch.maxant.kdc.mf.library.State@77643847
2021-01-03 15:30:03,938 INFO  [ch.max.kdc.mf.lib.State] (thread-2) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@706039eb


2021-01-03 15:37:17,253 INFO  [ch.max.kdc.mf.lib.Service] (thread-2) [request-id: c: e:] >>> SERVICE.CREATED ch.maxant.kdc.mf.library.Service@5192c8c2
2021-01-03 15:37:17,254 INFO  [ch.max.kdc.mf.lib.KafkaConsumers] (Quarkus Main Thread) [request-id: c: e:] kafka subscriptions setup completed
2021-01-03 15:37:17,254 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] >>> APP.CREATED ch.maxant.kdc.mf.library.Other@f77b711
2021-01-03 15:37:17,255 INFO  [ch.max.kdc.mf.lib.State] (thread-1) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@53321fc5
2021-01-03 15:37:17,256 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] set state to 1
2021-01-03 15:37:17,257 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] set state to 2
2021-01-03 15:37:18,387 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] doing it, state is 2
2021-01-03 15:37:20,194 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] doing it, state is 2
2021-01-03 15:37:20,731 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] doing it, state is 2
2021-01-03 15:37:21,812 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] doing it, state is 2

27676 thread 1 value 
27667 thread 2 27673map


https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Does.20.40RequestScoped.20only.20work.20with.20web.20requests.3F

    2021-01-03 16:23:14,880 INFO  [ch.max.kdc.mf.lib.Service] (thread-1) [request-id: c: e:] >>> SERVICE.CREATED ch.maxant.kdc.mf.library.Service@4384aed5
    2021-01-03 16:23:14,882 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] >>> APP.CREATED ch.maxant.kdc.mf.library.Other@29789459
    2021-01-03 16:23:14,882 INFO  [ch.max.kdc.mf.lib.State] (thread-1) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@374a5f6e
    2021-01-03 16:23:14,883 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] set state to 1 on ch.maxant.kdc.mf.library.State@374a5f6e
    2021-01-03 16:23:15,561 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] set state to 2 on ch.maxant.kdc.mf.library.State@374a5f6e
    2021-01-03 16:23:15,821 INFO  [ch.max.kdc.mf.lib.State] (Quarkus Main Thread) [request-id: c: e:] >>> STATE.DESTROYING ch.maxant.kdc.mf.library.State@374a5f6e
    2021-01-03 16:23:16,959 INFO  [ch.max.kdc.mf.lib.State] (thread-2) [request-id: c: e:] >>> STATE.CREATED ch.maxant.kdc.mf.library.State@2d584ec8
    2021-01-03 16:23:16,960 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] doing it, state is asdf on ch.maxant.kdc.mf.library.State@2d584ec8
    2021-01-03 16:23:18,388 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] doing it, state is asdf on ch.maxant.kdc.mf.library.State@2d584ec8
    2021-01-03 16:23:20,062 INFO  [ch.max.kdc.mf.lib.Other] (thread-2) [request-id: c: e:] doing it, state is asdf on ch.maxant.kdc.mf.library.State@2d584ec8
    2021-01-03 16:23:20,978 INFO  [ch.max.kdc.mf.lib.Other] (thread-1) [request-id: c: e:] doing it, state is asdf on ch.maxant.kdc.mf.library.State@2d584ec8
