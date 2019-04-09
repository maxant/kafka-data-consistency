# kafka-data-consistency

Experiement with using Kafka and idempotency to garantee data consistency by creating a reactive asynchronous system.

## Installing Kafka

    wget https://www-eu.apache.org/dist/kafka/2.1.1/kafka_2.11-2.1.1.tgz
    tar -xzf kafka_2.11-2.1.1.tgz
    echo kafka_2.11-2.1.1 >> .gitignore
    rm kafka_2.11-2.1.1.tgz
    git init

## Starting Kafka with docker

The generated `maxant/kafka` image uses a shell script to append important properties to the
`config/server.properties` file in the container, so that it works.
See `start-kafka.sh` for details, including how it MUST set
`advertised.listeners` to the containers IP address, otherwise kafka has
REAL PROBLEMS.

Run `./build.sh` which builds images for Zookeeper and Kafka

Run `./run.sh` which starts Zookeeper and two Kafka brokers with IDs 1 and 2,
listening on ports 9091 and 9092 respectively.

This script passes the Zookeeper

This script also shows how to append the hosts file so that service names can
be used by applications, but it requires the user to `sudo`. It also contains
examples of waiting for Zookeeper / Kafka logs to contain certain logs before
the script continues.


## Debug Web Component

    java -Ddefault.property=asdf -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -jar web/target/web-microbundle.jar

## Hot deploy with Payara

Instead of deploying the built WAR file, Payara allows you to start with an exploded folder. Maven happens to build one
during the package phase. You can redeploy by touching the `.reload` file.
So, deploy like this, e.g. the web component:

    java ... -jar <path to payara-micro-5.184.jar> --deploy web/target/web

Or in full:

    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar /home/ant/.m2/repository/fish/payara/extras/payara-micro/5.184/payara-micro-5.184.jar --deploy web/target/web

Then you can build and redeploy as follows, e.g. the web component:

    mvn -pl web package && touch web/target/web/.reload

More info: https://docs.payara.fish/documentation/payara-micro/deploying/deploy-cmd-line.html

# Links

- Microprofile Specs: https://github.com/eclipse/microprofile-health/releases/tag/1.0
- Microprofile POMs, etc.: https://github.com/eclipse/microprofile
- Payara Docs: https://docs.payara.fish/documentation/microprofile/
- Payara Examples: https://github.com/payara/Payara-Examples/tree/master/microprofile

# TODO

- add context of "partner" to filter on websocket server side
- finish build and run scripts
- UI
  - component tests + dependency mocking?
    - https://vue-test-utils.vuejs.org/guides/#getting-started
    - combine with JEST!
    - can we test it in the browser?
  - use resolver to avoid async code => except eg using an observable for updating server auto complete
      - example with addresses from post.ch
  - add claim page to view details of a claim
  - validation up front with http. if not available, then temp tile looks different => validation error should
    then be given to user as a task for them to fix
  - add aggregate for related claims, so we can show prototype of aggregated data
  - add search screen based on ES
  - see TODOs inside UI component
  - what are Vue.compile, extend, mixin, util?
  - https://www.codeinwp.com/blog/vue-ui-component-libraries/ => quasar
  - useful link for filters: https://vuejs.org/v2/guide/filters.html
  - useful link for flex: https://css-tricks.com/snippets/css/a-guide-to-flexbox/
  - useful link for validation: https://vuelidate.netlify.com/
  - useful link for vue-rxjs: https://github.com/vuejs/vue-rx
  - useful link for rxjs: https://www.learnrxjs.io/operators/creation/from.html
  - my question: https://forum.quasar-framework.org/topic/3391/how-can-i-hide-a-column
- example of error messages and e.g. security exceptions via error messages
- fixme consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
- add ES for search
- add kibana on top of ES?
- Tests with running server: https://groups.google.com/forum/#!topic/payara-forum/ZSRGdPkGKpE
  - starting server: https://blog.payara.fish/using-the-payara-micro-maven-plugin
  - https://docs.payara.fish/documentation/ecosystem/maven-plugin.html
- add extra jars to uberjar: https://blog.payara.fish/using-the-payara-micro-maven-plugin
- orientdb docker image => https://hub.docker.com/_/orientdb
- define payara config with yml
- add https://docs.payara.fish/documentation/microprofile/healthcheck.html and use it in start script?
- https://blog.payara.fish/using-hotswapagent-to-speed-up-development => [hotswapagent.md](hotswapagent.md)

# TODO Blog

- need lock when using transactional kafka, but not otherwise since producer is thread safe

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

- why doesnt vue have dependency injection? not really needed. or maybe when we go to test? defo then!

- observables => show example of the subscription to claims inside claims.js and
  how the template treats it as an object. v-for works as expected, but the thing
  is actually an observable. then show how we add to it by calling getValue in
  controller, using a BehaviourSubject. and we pass values from axios promise
  to a service obserable but manually put them into the subject by calling next. can we improve that?
