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
  - get import/export working with .mjs file
  - see TODOs inside UI component
  - .vue files?
  - use axios
  - add search screen based on ES
  - filters: https://vuejs.org/v2/guide/filters.html
  - Vue.compile, component, directive, extend, mixin, use, util
  - component tests
  - https://www.codeinwp.com/blog/vue-ui-component-libraries/ => quasar
  - responsive with 5 columns
- add partner and contract components for loading that data
- fixme consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
- add ES for search
- add kibana??
- add claim page to view details of a claim
- add aggregate for related claims, so we can show prototype of aggregated data
- add a vuejs form to learn about two way databinding
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