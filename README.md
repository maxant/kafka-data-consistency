# kafka-data-consistency

Experiement with using Kafka and idempotency to garantee data consistency by creating a reactive asynchronous system.

## Installing Kafka

Kafka needs to be present to build a suitable docker image.

    wget https://www-eu.apache.org/dist/kafka/2.1.1/kafka_2.11-2.1.1.tgz
    tar -xzf kafka_2.11-2.1.1.tgz
    #echo kafka_2.11-2.1.1 >> .gitignore
    rm kafka_2.11-2.1.1.tgz
    #git init

## Kubernetes

If necessary use the minikube docker host:

    eval $(minikube docker-env)

Run `./build.sh` after getting Kafka (see above).

Create a namespace:

    kubectl create -f namespace.json

Delete existing, if necessary:

    kubectl -n kafka-data-consistency delete deployment zookeeper
    kubectl -n kafka-data-consistency delete service zookeeper
    kubectl -n kafka-data-consistency delete deployment kafka-1
    kubectl -n kafka-data-consistency delete service kafka-1
    kubectl -n kafka-data-consistency delete deployment kafka-2
    kubectl -n kafka-data-consistency delete service kafka-2
    kubectl -n kafka-data-consistency delete deployment elasticsearch
    kubectl -n kafka-data-consistency delete service elasticsearch
    kubectl -n kafka-data-consistency delete deployment neo4j
    kubectl -n kafka-data-consistency delete service neo4j
    kubectl -n kafka-data-consistency delete deployment kibana
    kubectl -n kafka-data-consistency delete service kibana
    kubectl -n kafka-data-consistency delete deployment elastic-apm-server
    kubectl -n kafka-data-consistency delete service elastic-apm-server

Create deployments and services:

    kubectl -n kafka-data-consistency apply -f zookeeper.yaml
    kubectl -n kafka-data-consistency apply -f kafka-1.yaml
    kubectl -n kafka-data-consistency apply -f kafka-2.yaml
    kubectl -n kafka-data-consistency apply -f elasticsearch.yaml
    kubectl -n kafka-data-consistency apply -f neo4j.yaml
    kubectl -n kafka-data-consistency apply -f kibana.yaml
    kubectl -n kafka-data-consistency apply -f elastic-apm-server.yaml

Open ports like this:

    # zookeeper:30000:2181, kafka_1:30001:9092, kafka_2:30002:9092, neo4j:30101:7687, elastic-apm-server:30200:8200
    firewall-cmd --zone=public --permanent --add-port=30000/tcp
    firewall-cmd --zone=public --permanent --add-port=30001/tcp
    firewall-cmd --zone=public --permanent --add-port=30002/tcp
    firewall-cmd --zone=public --permanent --add-port=30101/tcp
    firewall-cmd --zone=public --permanent --add-port=30101/tcp
    firewall-cmd --zone=public --permanent --add-port=30200/tcp
    firewall-cmd --reload
    firewall-cmd --list-all

Setup forwarding like this (some are accessed directly from outside, others are accessed via nginx):

    # zookeeper, kafka_1, kafka_2
    socat TCP-LISTEN:30000,fork TCP:$(minikube ip):30000 &
    socat TCP-LISTEN:30001,fork TCP:$(minikube ip):30001 &
    socat TCP-LISTEN:30002,fork TCP:$(minikube ip):30002 &

    # elasticsearch
    socat TCP-LISTEN:30050,fork TCP:$(minikube ip):30050 &
    # only for inter node connections: socat TCP-LISTEN:30051,fork TCP:$(minikube ip):30051 &

    # neo4j
    socat TCP-LISTEN:30100,fork TCP:$(minikube ip):30100 &
    socat TCP-LISTEN:30101,fork TCP:$(minikube ip):30101 &

    # kibana
    socat TCP-LISTEN:30150,fork TCP:$(minikube ip):30150 &

    # elastic-apm-server
    socat TCP-LISTEN:30200,fork TCP:$(minikube ip):30200 &

    # minikube port, see kibana metricbeat for kube way down below
    socat TCP-LISTEN:10250,fork TCP:$(minikube ip):10250 &

Update nginx with a file under vhosts like this:

    #
    # created by ant, 20190423
    #

      # ############################################################
      # kdc.elasticsearch.maxant.ch
      # ############################################################

      server {
        listen 80;

        server_name kdc.elasticsearch.maxant.ch;
        location / {
            proxy_pass http://localhost:30050/;
        }
      }

      # ############################################################
      # kdc.neo4j.maxant.ch
      # ############################################################

      server {
        listen 80;

        server_name kdc.neo4j.maxant.ch;
        location / {
            proxy_pass http://localhost:30100/;
        }
      }

      # ############################################################
      # kdc.kibana.maxant.ch
      # ############################################################

      server {
        listen 80;

        server_name kdc.kibana.maxant.ch;
        location / {
            proxy_pass http://localhost:30150/;
        }
      }

      # ############################################################
      # minikube.maxant.ch - dashboard
      # ############################################################

      server {
        listen 80;

        server_name minikube.maxant.ch;
        location / {
            # uses socat port, so that we dont need to mess around with nginx if we have to restart the dashboard
            proxy_pass http://127.0.0.1:40000/api/v1/namespaces/kube-system/services/http:kubernetes-dashboard:/proxy/;
        }
      }

Restart nginx:

    systemctl restart nginx

Create topics (on minikube host):

    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic claim-create-db-command
    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic claim-create-search-command
    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic claim-create-relationship-command
    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic task-create-command
    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic claim-created-event
    kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic task-created-event
    kafka_2.11-2.1.1/bin/kafka-topics.sh --list --zookeeper $(minikube ip):30000

Create elasticsearch indexes:

    curl -X DELETE "kdc.elasticsearch.maxant.ch/claims"
    curl -X DELETE "kdc.elasticsearch.maxant.ch/partners"

    curl -X PUT "kdc.elasticsearch.maxant.ch/claims" -H 'Content-Type: application/json' -d'
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
                "summary": { "type": "text", "analyzer": "ants_analyzer" },
                "description": { "type": "text", "analyzer": "ants_analyzer" },
                "reserve": { "type": "double" },
                "date": { "type": "date", "format": "strict_date" }
            }
        }
    }

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
                "firstname": { "type": "text", "analyzer": "ants_analyzer" },
                "lastname": { "type": "text", "analyzer": "ants_analyzer" },
                "dob": { "type": "date", "format": "strict_date" },
                "zip": { "type": "text", "analyzer": "ants_analyzer" },
                "street": { "type": "text", "analyzer": "ants_analyzer" },
                "city": { "type": "text", "analyzer": "ants_analyzer" },
                "house": { "type": "text", "analyzer": "ants_analyzer" }
            }
        }
    }
    '

E.g. run task service locally, but connecting to kube:

    java -Xmx128M -Xms128M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 -jar web/target/web-microbundle.jar --port 8080 &
    java -Xmx128M -Xms128M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8788 -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 -Delasticsearch.baseUrl=kdc.elasticsearch.maxant.ch -Dneo4j.jdbc.url=jdbc:neo4j:bolt://kdc.neo4j.maxant.ch:30101 -Dneo4j.jdbc.username=a -Dneo4j.jdbc.password=a -jar claims/target/claims-microbundle.jar --port 8081 &
    java -Xmx128M -Xms128M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8789 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -javaagent:elastic-apm-agent-1.6.1.jar \
         -Delastic.apm.service_name=tasks \
         -Delastic.apm.server_urls=http://maxant.ch:30200 \
         -Delastic.apm.secret_token= \
         -Delastic.apm.application_packages=ch.maxant \
         -jar tasks/target/tasks-microbundle.jar \
         --port 8082 &

Useful Kube stuff:

    kubectl describe nodes

    # restart kube entirely, deleting everything
    minikube stop
    rm -rf ~/.minikube
    minikube delete
    minikube config set vm-driver kvm2
    minikube start --memory 8192 --cpus 4
    git clone https://github.com/kubernetes-incubator/metrics-server.git
    cd metrics-server/
    kubectl create -f deploy/1.8+/
    minikube addons enable metrics-server
    minikube dashboard &
    # make sure you note the port, and then run this, replacing the XXXXX from the output of the dashboard:
    socat TCP-LISTEN:40000,fork TCP:127.0.0.1:53885 &

    # connect to the vm. eg. top and stuff to see whats going on inside.
    minikube ssh

    # create a deployment
    kubectl run hello-minikube --image=k8s.gcr.io/echoserver:1.10 --port=8080
    # create a service from a deployment
    kubectl expose deployment hello-minikube --type=NodePort

## Starting Kafka with docker

WARNING: this section may be slightly out of date!

The generated `maxant/kafka` image uses a shell script to append important properties to the
`config/server.properties` file in the container, so that it works.
See `start-kafka.sh` for details, including how it MUST set
`advertised.listeners` to the containers IP address, otherwise kafka has
REAL PROBLEMS.

Run `./build.sh` which builds images for Zookeeper and Kafka

Run `./run.sh` which starts Zookeeper and two Kafka brokers with IDs 1 and 2,
listening on ports 9091 and 9092 respectively.

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

- neo4j: think of scenario where more relationship are involved, which a relational db would struggle with
- add image for orientdb
  - orientdb docker image => https://hub.docker.com/_/orientdb
- add context of "partner" to filter on websocket server side
- finish build and run scripts
- dockerize ui, tasks, claims, web
- kubernetes/prometheus/grafana: https://raw.githubusercontent.com/giantswarm/kubernetes-prometheus/master/manifests-all.yaml
- still need to think about transaction when writing to own DB and informing UI that a change took place. maybe use CDC?
- validation up front with http. if not available, then temp tile looks different => validation errors
  during actual async processing of kafka record should then be given to user as a task for them to fix
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

# TODO Blog

- need lock when using transactional kafka, but not otherwise since producer is thread safe


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

# Useful Elasticsearch stuff:

- http://elasticsearch-cheatsheet.jolicode.com/
- Info about indices

    curl -X GET "kdc.elasticsearch.maxant.ch/_cat/indices?v"

    health status index  uuid                   pri rep docs.count docs.deleted store.size pri.store.size
    yellow open   claims 5SY5zm3bRxCgeTAac4wazQ   1   1          1            0      6.2kb          6.2kb

- Create and index a document:

    curl -X PUT "kdc.elasticsearch.maxant.ch/claims/_doc/b5565b5c-ab65-4e00-b562-046e0d5bef70?pretty" -H 'Content-Type: application/json' -d'
    {
        "id" : "b5565b5c-ab65-4e00-b562-046e0d5bef70",
        "summary" : "the cat ate the frog",
        "description" : "L\u0027avion volé à Paris mit 2 Hünde an bord, and arrived in two hours flat!",
        "partnerId" : "P-1234-5678",
        "date" : "2018-08-01",
        "reserve" : 9100.05
    }
    '

- Pretty print a regexp query (note `?pretty`):

    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "regexp" : { "description" : ".*m\u0027avion.*" } } }'

NOTE that a regexp query does not use analyzers!!

- field types: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html

- analyse an analyzer:

    curl -X POST "kdc.elasticsearch.maxant.ch/claims/_analyze?pretty" -H 'Content-Type: application/json' -d'
    {
      "analyzer": "ants_analyzer",
      "text": "L\u0027avion volé à Paris mit 2 Hünde an bord, and arrived in two hours flat!"
    }
    '

- Or to analyse how a field might match:

    curl -X POST "kdc.elasticsearch.maxant.ch/claims/_analyze?pretty" -H 'Content-Type: application/json' -d'
    {
      "field": "description",
      "text": "arrives"
    }
    '

- Try these two matches queries:

    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "match" : { "description" : "*arrives*" } } }'
    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "match" : { "description" : "m\u0027avion" } } }'

The first matches even though the record contains "arrived". The second matches `m'avion` rather than `L'avion` in the document.

- keyword query:

    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "match" : { "partnerId" : "P-1234-5678" } } }'

- count:

    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_count?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "match" : { "partnerId" : "P-1234-5678" } } }'

- range query, in ALL indexes:

    curl -X GET "kdc.elasticsearch.maxant.ch/_all/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "range" : { "reserve" : {"gt": 9000 } } } }'

- query, in a LIST of indexes:

    curl -X GET "kdc.elasticsearch.maxant.ch/anIndex,anotherIndex/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "range" : { "reserve" : {"gt": 9000 } } } }'

## Links

- ES 7.0 REST High Level Client Javadocs: https://artifacts.elastic.co/javadoc/org/elasticsearch/client/elasticsearch-rest-high-level-client/7.0.0/index-all.html
- Generic Reference: https://www.elastic.co/guide/en/elasticsearch/reference/current/getting-started-query-document.html
- Java Client: https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high-count.html

# Useful Neo4j stuff:

- Go to http://kdc.neo4j.maxant.ch
- Enter `bolt://kdc.neo4j.maxant.ch:30101` as the backend URL
- Enter blank username & password, because of env variable in neo4j.yaml
- Creating nodes and relationships at the same time:

  `ee` is a variable; `Person` is a label (class?); properties are inside curlies
  `[]` are relationships, `()` are nodes

    CREATE (ee:Person { name: "Emil", from: "Sweden", klout: 99 }),
           (js:Person { name: "Johan", from: "Sweden", learn: "surfing" }),
           (ir:Person { name: "Ian", from: "England", title: "author" }),
           (ee)-[:KNOWS {since: 2001}]->(js),
           (ee)-[:KNOWS {rating: 5}]->(ir),
           (js)-[:KNOWS]->(ir),
           (ir)-[:KNOWS]->(js)

- matching:

    MATCH (js:Person)-[:KNOWS]-()-[:KNOWS]-(surfer)
    WHERE js.name = "Johan" AND surfer.hobby = "surfing"
    RETURN DISTINCT surfer

- Creating master data:

    MATCH (n)
    DETACH DELETE n

    MATCH (n)-[r]-(n2)
    RETURN *

    CREATE (c1:Contract { id: "V-9087-4321" }),
           (c2:Contract { id: "V-8046-2304" }),
           (c3:Contract { id: "V-7843-4329" }),
           (c4:Contract { id: "V-6533-9832" }),
           (p1:Partner {id: "P-4837-4536"}),
           (p2:Partner {id: "P-1234-5678"}),
           (p3:Partner {id: "P-9873-0983"}),
           (p1)-[:POLICY_HOLDER]->(c1),
           (p1)-[:POLICY_HOLDER]->(c2),
           (p2)-[:POLICY_HOLDER]->(c3),
           (p3)-[:POLICY_HOLDER]->(c4),
           (cl1:Claim { id: "b5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-27" }),
           (p1)-[:CLAIMANT]->(cl1),
           (cl2:Claim { id: "c5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-25" }),
           (p1)-[:CLAIMANT]->(cl2),
           (cl3:Claim { id: "d5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-22" }),
           (p2)-[:CLAIMANT]->(cl3),
           (cl4:Claim { id: "e5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-26" }),
           (p3)-[:CLAIMANT]->(cl4),
           (cl5:Claim { id: "f5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-25" }),
           (p3)-[:CLAIMANT]->(cl5),
           (cl6:Claim { id: "05565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-24" }),
           (p3)-[:CLAIMANT]->(cl6)

    MATCH (n)-[r]-(n2)
    RETURN *

    CREATE CONSTRAINT ON (p:Partner) ASSERT p.id IS UNIQUE
    CREATE CONSTRAINT ON (c:Contract) ASSERT c.id IS UNIQUE
    CREATE CONSTRAINT ON (c:Claim) ASSERT c.id IS UNIQUE
    CREATE INDEX ON :Claim(date)

    MATCH (n)-[r]-(n2)
    RETURN *

- constraints: https://neo4j.com/docs/getting-started/current/cypher-intro/schema/#cypher-intro-schema-constraints

    CREATE CONSTRAINT ON (p:Partner) ASSERT p.id IS UNIQUE

    CREATE CONSTRAINT ON (c:Contract) ASSERT c.id IS UNIQUE

    CREATE CONSTRAINT ON (c:Claim) ASSERT c.id IS UNIQUE

    call db.constraints()

- indices: https://neo4j.com/docs/cypher-manual/current/schema/index/ - think about queries and add indexes accordingly

    CREATE INDEX ON :Claim(date)

    call db.indexes()

- Creating a relationship to an existing node:

  - A claim:

    MATCH (p:Partner) WHERE p.id = "P-4837-4536"
    CREATE (c:Claim { id: "b5565b5c-ab65-4e00-b562-046e0d5bef70", date: "2019-04-27" }),
           (p)-[:CLAIMANT]->(c)

  - Coverage:

    MATCH (contract:Contract), (claim:Claim)
    WHERE contract.id = "V-8046-2304" AND claim.id = "b5565b5c-ab65-4e00-b562-046e0d5bef70"
    CREATE (claim)-[:COVERED_BY]->(contract)

- Match everything from a partner (loads all relationships too; selects any nodes attached to a partner in any direction):

    MATCH (n)--(p:Partner)
    WHERE p.id = "P-4837-4536"
    RETURN p, n

- Match everything:

    MATCH (n)-[r]-() RETURN n, r

- Match claims:

    MATCH (n)-[r:CLAIMANT]->(m) RETURN *

- delete all nodes and relationships:

    MATCH (n)
    DETACH DELETE n

- delete all relationshiops matching:

    MATCH (n { name: 'Andy' })-[r:KNOWS]->()
    DELETE r

- find all labels (yep, its cypher):

    call db.labels()

- show schema (layout):

    call db.schema()

- number of claims per partner between given dates:

    match (claim:Claim)<-[claimant:CLAIMANT]-(p:Partner)
    where claim.date > '2019-04-24'
      and claim.date < '2019-04-26'
    with count(claimant) as numClaims, p
    return numClaims, p

- fraud detection - find customers with more than x claims since a given date

    MATCH (claim:Claim)<-[r:CLAIMANT]-(p:Partner)
    WHERE claim.date > '2018-08-01'
    WITH count(r) as cnt, p
    WHERE cnt > 1
    RETURN cnt, p.id

  ok, we can do this with a relational database. What is more interesting
  is when there are lots of relationships.

- visualise the partners graph but just the relevant claims where the partner
  has more than X claims in the given period:

    match (claim:Claim)<-[claimant:CLAIMANT]-(p:Partner)
    where claim.date >= '2019-04-24'
      and claim.date <= '2019-04-26'
    with count(claimant) as numClaims, p
    where numClaims > 2
    with p.id as id
    match (cl:Claim)<-[claimant2:CLAIMANT]-(partner:Partner)-[ph:POLICY_HOLDER]->(contract:Contract)
    where partner.id = id
      and cl.date >= '2019-04-24'
      and cl.date <= '2019-04-26'
    return *

- TODO
  - explain
  - profile

# Useful Kibana stuff:

- (ideas taken from https://mherman.org/blog/logging-in-kubernetes-with-elasticsearch-Kibana-fluentd/#minikube)
- (not used directly: https://www.elastic.co/guide/en/kibana/current/docker.html)
- Go to http://kdc.kibana.maxant.ch
- manage indexes here: http://kdc.kibana.maxant.ch/app/kibana#/management/elasticsearch/index_management/indices?_g=()
- create an index for `claims` using `date` as the time field
- find all claims in a period:
  - http://kdc.kibana.maxant.ch/app/kibana#/discover?_g=(refreshInterval:(pause:!t,value:0),time:(from:'2019-03-01T15:15:58.115Z',to:now))&_a=(columns:!(_source),index:eb741a10-69c1-11e9-9ebe-bf41a21a544a,interval:auto,query:(language:kuery,query:''),sort:!(date,desc))
  - visualise sum of reserve per day: http://kdc.kibana.maxant.ch/app/kibana#/visualize/create?_g=(refreshInterval:(pause:!t,value:0),time:(from:'2019-03-01T15:15:58.115Z',to:now))&_a=(filters:!(),linked:!f,query:(language:kuery,query:''),uiState:(),vis:(aggs:!((enabled:!t,id:'2',params:(field:reserve),schema:metric,type:sum),(enabled:!t,id:'3',params:(customInterval:'2h',drop_partials:!f,extended_bounds:(),field:date,interval:d,min_doc_count:1,timeRange:(from:'2019-03-01T15:15:58.115Z',to:now),time_zone:Europe%2FZurich,useNormalizedEsInterval:!t),schema:segment,type:date_histogram)),params:(addLegend:!t,addTimeMarker:!f,addTooltip:!t,categoryAxes:!((id:CategoryAxis-1,labels:(show:!t,truncate:100),position:bottom,scale:(type:linear),show:!t,style:(),title:(),type:category)),grid:(categoryLines:!f),legendPosition:right,seriesParams:!((data:(id:'2',label:'Sum%20of%20reserve'),drawLinesBetweenPoints:!t,mode:stacked,show:!t,showCircles:!t,type:histogram,valueAxis:ValueAxis-1)),times:!(),type:histogram,valueAxes:!((id:ValueAxis-1,labels:(filter:!f,rotate:0,show:!t,truncate:100),name:LeftAxis-1,position:left,scale:(mode:normal,type:linear),show:!t,style:(),title:(text:'Sum%20of%20reserve'),type:value))),title:'',type:histogram))&indexPattern=eb741a10-69c1-11e9-9ebe-bf41a21a544a&type=histogram

# Beats: Metrics and Logging

## Kube & Kafka metric beat: http://kdc.kibana.maxant.ch/app/kibana#/home/tutorial/kubernetesMetrics?_g=()

    curl -L -O https://artifacts.elastic.co/downloads/beats/metricbeat/metricbeat-7.0.0-linux-x86_64.tar.gz
    tar xzvf metricbeat-7.0.0-linux-x86_64.tar.gz
    cd metricbeat-7.0.0-linux-x86_64/

    vi metricbeat.yml

Edit:

    output.elasticsearch:
      hosts: ["localhost:30050"]

    setup.kibana:
      host: "localhost:30150"

Then:

    ./metricbeat modules enable kubernetes

See `modules.d/kubernetes.yml`, but it only really needs access to `localhost:10250`, so using `socat` we've mapped it to minikube. see that way above where socat is started.
comment out the following:

    #  bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
    #  ssl.certificate_authorities:
    #    - /var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt

    ./metricbeat modules enable kafka

    vi modules.d/kafka.yml

Update:

    hosts: ["localhost:30001", "localhost:30002"]

Then:

    ./metricbeat setup

    ./metricbeat -e >/dev/null 2>&1 &

Note: the "check data" button on the Kibana setup page doesn't seem to work. But, check dashboards and the data is there.
Eg:

- Dashboard => Metricbeat Kafka Overview ECS
- Dashboard => Kubernetes => doesnt seem to work...

## Filebeats for Kube Pods: https://www.elastic.co/guide/en/beats/filebeat/current/running-on-kubernetes.html

(see this too, although I didn't entirely follow it: https://mherman.org/blog/logging-in-kubernetes-with-elasticsearch-Kibana-fluentd/#minikube)
(ideas: https://www.elastic.co/downloads/beats/filebeat)
(ideas: https://www.elastic.co/guide/en/beats/filebeat/current/filebeat-installation.html)

See https://www.elastic.co/guide/en/beats/filebeat/current/running-on-kubernetes.html

Use the file checked in here, which is configured to use auto discover.

    kubectl create -f filebeat-kubernetes.yaml

Which outputs:

    configmap/filebeat-config created
    configmap/filebeat-inputs created
    daemonset.extensions/filebeat created
    clusterrolebinding.rbac.authorization.k8s.io/filebeat created
    clusterrole.rbac.authorization.k8s.io/filebeat created
    serviceaccount/filebeat created

That can be reset with:

    kubectl -n kube-system delete configmaps filebeat-config
    kubectl -n kube-system delete configmaps filebeat-inputs
    kubectl -n kube-system delete daemonsets filebeat
    kubectl -n kube-system delete clusterrolebindings filebeat
    kubectl -n kube-system delete clusterroles filebeat
    kubectl -n kube-system delete serviceaccounts filebeat

Create a filebeat index in kibana, and away you go.

Use KQL like this:

    kubernetes.container.name=neo4j


    kubernetes.namespace: "kafka-data-consistency" and (kubernetes.container.name : "kafka-1" or kubernetes.container.name : "kafka-2")

Or add a filter:

    {
      "query": {
        "regexp": {
          "kubernetes.container.name": {
            "value": "kafka-."
          }
        }
      }
    }

http://kdc.kibana.maxant.ch/app/kibana#/discover?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-2h,to:now))&_a=(columns:!(message),filters:!(('$state':(store:appState),meta:(alias:!n,disabled:!f,index:c1be7050-69d3-11e9-9ebe-bf41a21a544a,key:kubernetes.namespace,negate:!f,params:(query:kafka-data-consistency),type:phrase,value:kafka-data-consistency),query:(match:(kubernetes.namespace:(query:kafka-data-consistency,type:phrase)))),('$state':(store:appState),meta:(alias:!n,disabled:!f,index:c1be7050-69d3-11e9-9ebe-bf41a21a544a,key:query,negate:!f,type:custom,value:'%7B%22regexp%22:%7B%22kubernetes.container.name%22:%7B%22value%22:%22kafka-.%22%7D%7D%7D'),query:(regexp:(kubernetes.container.name:(value:kafka-.))))),index:c1be7050-69d3-11e9-9ebe-bf41a21a544a,interval:auto,query:(language:kuery,query:'kubernetes.namespace:%20%22kafka-data-consistency%22%20and%20(kubernetes.container.name%20:%20%22kafka-1%22%20or%20kubernetes.container.name%20:%20%22kafka-2%22)'),sort:!('@timestamp',desc))

# Tracing

Chose to use elasticsearch's APM Server - it's opentracing according to opentracing.org
And it is well documented.
Not yet convinced that the microprofile supports full propagation as well as tracing jdbc etc.

- See https://www.elastic.co/guide/en/apm/server/current/running-on-docker.html
- Install APM Server into Kube
- Once APM server is running in Kube, go to kibana home page and setup APM: http://kdc.kibana.maxant.ch/app/kibana#/home/tutorial/apm?_g=()
- click the button "check apm server status"
- Download `elastic-apm-agent-1.6.1.jar` from https://search.maven.org/search?q=a:elastic-apm-agent
- Add the following to the launch config:

         -javaagent:elastic-apm-agent-1.6.1.jar \
         -Delastic.apm.service_name=tasks \
         -Delastic.apm.server_urls=http://maxant.ch:30200 \
         -Delastic.apm.secret_token= \
         -Delastic.apm.application_packages=ch.maxant \

- start the app
- click the rest of the buttons in the kibana webapp
- view data: http://kdc.kibana.maxant.ch/app/apm#/services

