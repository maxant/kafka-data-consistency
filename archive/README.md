# kafka-data-consistency

Experiement with using Kafka and idempotency to garantee data consistency by creating a reactive asynchronous system.

## Architecture

### Loading the page in the browser

![Loading the page in the browser](http://www.plantuml.com/plantuml/svg/5Sqn3i8m38NXdLF00PhiJBq9CeuXKHEtify5t9vqUa_wXdzra256lzoQSeyYOVrQWSFlEHjzqhkXnZDSZ7U5A1Bl8m_waY9lzDfeAGHQfdfpYF9lwZoMoRWs22DC7YPKqd6MLj4ozmy0)

### Simplified view of creating a claim

![Simplified view of creating a claim](http://www.plantuml.com/plantuml/svg/5Or13i8m30Jll08UqEREgJ-fZ6kerIIDxBM0tsDEizAiD8k33D7gvQQTQsmILgyxCFnUYj5xVYdsU8IByTaX7JEVhJJwK4Svw7dGj15eIklgUU1lsXnMo7XN22DCLZOX92ffMrtRg3AFVtYHlYL_)

### Playing with Kafka Streams and KSQL

![Playing with Kafka Streams and KSQL](http://www.plantuml.com/plantuml/svg/5SqnhW8n38JX_ftYSO1DUggz2JAE8T4ctiWU2xWzePgHlkBV7MI8qVykJRb7aR3-Nu7frvoDNkdPKECUBiQdGXI9pv47lKcH3teSj1K2RKkzEKJvitGTosHS6uGH9hT91XNpD7-rNRIi_G80)

### Complex view of creating a claim

![Complex view of creating a claim](http://www.plantuml.com/plantuml/svg/5Or1hi8m34Jt_nIV0xJPi-fDqE0O4ZL9AsSiuFQOrHlfZVIsIHYzjxzB7T8ygrlxB-GvhoaPNkkpilZTfveSWpguoj5Jnnk3QSTZnv91B65ddN6GJzWObc1IAbNefZSn1GCDjQ_dhxZfoC4l)

### How to generate UML

See https://stackoverflow.com/a/32771815/458370

Put link to raw puml file into this: http://www.plantuml.com/plantuml/form, and use the output single line URL in the above link, e.g.:

    @startuml
    !includeurl https://raw.githubusercontent.com/maxant/kafka-data-consistency/master/architecture_create_claim_simple.puml
    @enduml

# TODO

- interesting, whats this do exactly? `docker system prune -f --volumes`
- migrate web to webq
- read https://smallrye.io/smallrye-reactive-messaging/
- make web subscribe to sink of a KSQL with windowed average age of new young partners
- build function to reset all data to masterdata
- fix tracing - we don't get any data at the moment!
- add creation time to claim and use that as index in kibana
- add user to claim to see who created it
- add jwt from microprofile?
- contract history and embedded neo4j with bolt vs table with graph identifier column
- coverage selection, mapping contract to claim
- need to build the contract component which can do contract history
- use mysql to save tasks. dont bother saving claims for now, other than in memory
- neo4j: think of scenario where more relationships are involved, which a relational db would struggle with
    - partners are unrelated. but claims are related over locations and a little over partners. and over products. contracts are related over location and product.
    - but none of the objects in the model relate to the same over an unknown number of relationships.
        - altho they could be if we added related products that were bought by other customers
        - what about this: ()-[1..*]-()-[*]-() hmmm not sure right now...
        - history!: (:Contract)<-[1..*:REPLACED_BY]-(:Contract)-[1..*:REPLACES]->(:Contract)
- use microprofile open tracing, rather than lib from elastic, where possible
- add context of "partner" to filter on websocket server side
- finish build and run scripts
- dockerize ui, tasks, claims, web, locations
- still need to think about transaction when writing to own DB and informing UI that a change took place. maybe use CDC?
- validation up front with http. if not available, then temp tile looks different => validation errors
  during actual async processing of kafka record should then be given to user as a task for them to fix
- add image for orientdb
    - orientdb docker image => https://hub.docker.com/_/orientdb
- UI
    - add claim page to view details of a claim
    - add aggregate for related claims, so we can show prototype of aggregated data
    - move claim form to own view
    - use resolver to avoid async code => except eg using an observable for updating server auto complete
        - example with addresses from post.ch
    - see TODOs inside UI component
    - what are Vue.compile, extend, mixin, util?
    - https://www.codeinwp.com/blog/vue-ui-component-libraries/ => quasar
    - useful link for filters: https://vuejs.org/v2/guide/filters.html
    - useful link for flex: https://css-tricks.com/snippets/css/a-guide-to-flexbox/
    - useful link for validation: https://vuelidate.netlify.com/
    - useful link for vue-rxjs: https://github.com/vuejs/vue-rx
    - useful link for rxjs: https://www.learnrxjs.io/operators/creation/from.html
    - useful tip: console.dir(document.getElementById("id")) => shows an object rather than the rendered element
    - useful tip: document.getElementById("claims-form-other").__vue__ => gets the vue component
    - useful tip: document.getElementById("claims-form-other").__vue__.$refs.input.focus() => set focus on it. not sure this is the correct way to do it tho!
    - useful tip: document.getElementById("claims-form-other").__vue__.rules.map(function(f){return f(document.getElementById("claims-form-other").__vue__.$refs.input.value)}) => execute all internal validation rules on the component
    - my question: https://forum.quasar-framework.org/topic/3391/how-can-i-hide-a-column
    - my question: https://forum.quasar-framework.org/topic/3437/unit-testing-and-simulating-input
    - my question: https://forum.quasar-framework.org/topic/3438/form-validation
- example of error messages and e.g. security exceptions via error messages
- fixme consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
- Tests with running server: https://groups.google.com/forum/#!topic/payara-forum/ZSRGdPkGKpE
    - starting server: https://blog.payara.fish/using-the-payara-micro-maven-plugin
    - https://docs.payara.fish/documentation/ecosystem/maven-plugin.html
- add extra jars to uberjar: https://blog.payara.fish/using-the-payara-micro-maven-plugin
- define payara config with yml
- add https://docs.payara.fish/documentation/microprofile/healthcheck.html and use it in start script?
- https://blog.payara.fish/using-hotswapagent-to-speed-up-development => [hotswapagent.md](hotswapagent.md)
- Strange problem with Bold driver in payara micro:

  [2019-05-01T23:37:54.931+0200] [] [INFO] [AS-WEB-GLUE-00201] [javax.enterprise.web] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1556746674931] [levelValue: 800] Virtual server server loaded default web module
  [2019-05-01T23:38:07.802+0200] [] [WARNING] [] [javax.enterprise.web.util] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1556746687802] [levelValue: 900] The web application [unknown] registered the JDBC driver [org.neo4j.jdbc.bolt.BoltDriver] but failed to unregister it when the web application was stopped. To prevent a memory leak, the JDBC Driver has been forcibly unregistered.
  [2019-05-01T23:38:07.804+0200] [] [WARNING] [] [javax.enterprise.web.util] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1556746687804] [levelValue: 900] The web application [unknown] registered the JDBC driver [org.neo4j.jdbc.boltrouting.BoltRoutingNeo4jDriver] but failed to unregister it when the web application was stopped. To prevent a memory leak, the JDBC Driver has been forcibly unregistered.
  [2019-05-01T23:38:07.805+0200] [] [WARNING] [] [javax.enterprise.web.util] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1556746687805] [levelValue: 900] The web application [unknown] registered the JDBC driver [org.neo4j.jdbc.http.HttpDriver] but failed to unregister it when the web application was stopped. To prevent a memory leak, the JDBC Driver has been forcibly unregistered.

- APM
    - jdbc driver - mysql works, neo4j doesnt, i guess its not jdbc4 conform?
    - async EJB or managed executor
    - change call to tasks to be an example of mandatory validation => you dont really want that, you want to be able to get a human to intervene async
        - then move task creation back to kafka
    - only spans show stack traces
    - remove span around recordHandler?
    - is it possible to add logging to the traces? so that logging and tracing are together?
        - if we had a UUID as a label or tag or something which was also in MDC, that would work...

# TODO Blog

- need lock when using transactional kafka, but not otherwise since producer is thread safe

- locations in ES are saved as part of the claims document - how are they indexed? can we search for them?
    - yes they are in there, and they can be searched for

- objective: use a light weight UI technology that does not require us to have a build system
    - assume http/2 so that multiple fetches don't worry us
    - need lazy loading => see example in boot.js

- vuex says "Vuex uses a single state tree - that is, this single object contains all your application level state and serves as the "single source of truth". This also means usually you will have only one store for each application." (https://vuex.vuejs.org/guide/state.html)
- it also says "So why don't we extract the shared state out of the components, and manage it in a global singleton? With this, our component tree becomes a big "view", and any component can access the state or trigger actions, no matter where they are in the tree!" (https://vuex.vuejs.org/)
- thats a conflict :-)
- compare http://blog.maxant.co.uk/pebble/2008/01/02/1199309880000.html and http://blog.maxant.co.uk/pebble/images/ants_mvc.jpg with https://github.com/facebook/flux/tree/master/examples/flux-concepts and https://github.com/facebook/flux/blob/master/examples/flux-concepts/flux-simple-f8-diagram-with-client-action-1300w.png
- vuex also doesn't match what flux says: "There should be many stores in each application." (https://github.com/facebook/flux/tree/master/examples/flux-concepts)
- see video here: https://facebook.github.io/flux/ at 11:03 (screen shot at ./ui/mvc-incorrect-as-far-as-ant-thinks.png) (https://www.youtube.com/watch?list=PLb0IAmt7-GS188xDYE-u1ShQmFFGbrk0v&time_continue=659&v=nYkdrAPrdcw)
- i believe that the arrows going from view back to model are a result of the "separable model architecture" well known in java swing.
- "So we collapsed these two entities (view and controller) into a single UI object" (https://www.oracle.com/technetwork/java/architecture-142923.html) => that leads to arrows as shown where the view directly updates the model. it is also the problem with two way binding (https://stackoverflow.com/questions/38626156/difference-between-one-way-binding-and-two-way-binding-in-angularjs)
- don't two-way-bind WITH THE STORE. instead, get forms to fill their own model, and pass that to the CONTROLLER to merge the changes into the main model
- to ensure that you only write to the model inside the controller
- this can be inforced by only importing the model in the main components which
  create their controllers, e.g. the `PartnerView`:

  import {model} from './model.js';
  import {Store} from './store.js';
  import {Controller} from './controller.js';

  const store = new Store(model);
  const controller = new Controller(store, model);

- why doesnt vue have dependency injection? not really needed. or maybe when we go to test? defo then! or maybe not,
  see example with "mocks"
    - WRONG - it does exist. see `provide` and `inject` in `claims.js` and `partnerView.js`. mocking works then, see `claims.spec.js`.

- observables => show example of the subscription to claims inside claims.js and
  how the template treats it as an object. v-for works as expected, but the thing
  is actually an observable. then show how we add to it by calling getValue in
  controller, using a BehaviourSubject. and we pass values from axios promise
  to a service obserable but manually put them into the subject by calling next. can we improve that?

- async route loading via custom loader since vue supports creating routes with promises

- APM

- Temporal Problems
  Came across the problem of not being able to create the location, which creates the relationship to the claim at the same time, because that record was processed
  before the record which creates the claim in Neo4J. The problem is that there is a dependency between writing the two data sets (the claim; the location with
  the relationship to the claim) which turns into a timing issue in the design where the components are chosen by their responsibilities for certain business
  entities. I can think of three solutions to this problem:

    - build some logic which recognises that dependent data is being processed too early, and put it somewhere until later,
    - build logic into both components so that the relationship between the claim and the location is created by the second component to run,
    - use a Kafka partition to provide a solution to this ordering problem, because partitions guarantee order.

The first solution begs the question of where to store the data and when to then process it. The second solution is a simple solution for data stores like
Neo4J where the relationship can be created by either component, but wouldn't work with a relational database with a foreign key constraint, because the
child record cannot be created until after the parent record has been. So I chose to use the third solution, and at the same time, formalise it with a design
rule:

_"If a data store enforces constraints on it's data model, for example as a relational database does when inserting child records where foreign keys
constraints are specified, then the insertion order of the data becomes important.  As soon as ordering is important, it becomes necessary to encapsulate
that data store behind a Kafka partition, so that there is a guarantee of the order of the incoming records."_

The claims component creates the Kafka record to persist the claim in Neo4J, and the command to create the location in the location component, which in turn
adds the location to Neo4J and the relationship between the claim and the location.
So change that design and encapsulate Neo4J behind a component named "graphs", and let the UI call the
claim component which creates a command record in Kafka to add the claim to the analytical component, and a command record to create the location.
The location component will create a command record to create the location in the analytical component at some time after the claim command record was created
and because both records use the claim ID as the key, the timing problem is automatically solved.

