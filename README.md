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

## Installing Kafka

Kafka needs to be present to build a suitable docker image.

    wget https://downloads.apache.org/kafka/2.7.0/kafka_2.12-2.7.0.tgz
    tar -xzf kafka_2.12-2.7.0.tgz
    #echo kafka_2.12-2.7.0 >> .gitignore
    rm kafka_2.12-2.7.0.tgz
    #git init

## Use docker-compose

(minikube and others either didnt work on CentOS7 or used too much CPU - see [kube.md](kube.md))

see `docker-compose.yml`

Make sure the necessary volume folder exists:

    mkdir /portainer_data
    mkdir confluent-hub-components
    mkdir confluent-hub-components/confluentinc-kafka-connect-jdbc
    mkdir mysql-data

Access Portainer here: http://portainer.maxant.ch/

    # start everything
    docker-compose -f dc-base.yml up -d

    # undeploy and entirely remove just one of the services
    docker-compose -f dc-base.yml rm -fsv kdc-ksqldb-server
    docker-compose -f dc-base.yml rm -fsv kdc-kafka-1
    docker-compose -f dc-ksqldb.yml rm -fsv kdc-ksqldb-server

    # redeploy just missing services
    docker-compose -f ... up -d --remove-orphans

Monitor docker CPU/Memory with:

    docker stats
    
or open http://cadvisor.maxant.ch/

`socat` can be useful for port-forwarding to docker containers.

export DC_IPA=$(docker inspect kdc-kafka-1 | jq '.[0]["NetworkSettings"]["Networks"]["kafka-data-consistency_default"]["IPAddress"]')
DC_IPA="${DC_IPA%\"}"
DC_IPA="${DC_IPA#\"}"
socat TCP-LISTEN:30401,fork TCP:$DC_IPA:9876 &

Open ports like this:

    # port list: not everything is exposed!
    # =======================================
    #                    grafana:29993:3000  mapped in nginx
    #                 prometheus:29996:9000  mapped in nginx
    #                  portainer:29999:9000  mapped in nginx
    #                  zookeeper:30000:2181  exposed
    #                    kafka_1:30001:9092  exposed
    #                    kafka_2:30002:9092  exposed
    #           kafka-rest-proxy:30030:8082  inactive?
    #                    elastic:30050:9200  mapped in nginx
    #                    elastic:30051:9300  hidden
    #                   logstash:30055:12201 exposed
    #                   logstash:30056:5000  hidden
    #                   logstash:30057:9600  hidden
    #                    kafdrop:30060:9000  mapped in nginx
    #                      neo4j:30101:7687  exposed
    #                     kibana:30150:5601  mapped in nginx
    #         elastic-apm-server:30200:8200  exposed
    #                      mysql:30300:3306  exposed
    #              ksql-server-1:30401:8088  exposed, inactive
    #              ksql-server-2:30402:8088  exposed, inactive
    #            ksqldb-server-1:30410:8088  exposed, inactive
    #   confluent-control-center:30500:9021  exposed, inactive
    #             schemaregistry:30550:8085  mapped in nginx
    #          schemaregistry-ui:30555:8000  mapped in nginx
    #               jaeger-agent:30561:6831  exposed
    #               jaeger-query:30570:16686 mapped in nginx
    #                kdc-objects:30601:8086  inactive
    #           kdc-mf-contracts:30780:8080  mapped in nginx
    #             kdc-mf-pricing:30781:8081  mapped in nginx
    #                 kdc-mf-web:30782:8082  mapped in nginx
    #            kdc-mf-partners:30783:8083  mapped in nginx
    #               kdc-mf-cases:30784:8084  mapped in nginx
    #         kdc-mf-waitingroom:30785:8085  mapped in nginx
    #        kdc-mf-organisation:30786:8086  mapped in nginx
    firewall-cmd --zone=public --permanent --add-port=30000/tcp
    firewall-cmd --zone=public --permanent --add-port=30001/tcp
    firewall-cmd --zone=public --permanent --add-port=30002/tcp
    firewall-cmd --zone=public --permanent --add-port=30055/udp
    firewall-cmd --zone=public --permanent --add-port=30101/tcp
    firewall-cmd --zone=public --permanent --add-port=30200/tcp
    firewall-cmd --zone=public --permanent --add-port=30300/tcp
    firewall-cmd --zone=public --permanent --add-port=30401/tcp
    firewall-cmd --zone=public --permanent --add-port=30402/tcp
    firewall-cmd --zone=public --permanent --add-port=30410/tcp
    firewall-cmd --zone=public --permanent --add-port=30500/tcp
    firewall-cmd --zone=public --permanent --add-port=30560/tcp
    firewall-cmd --zone=public --permanent --add-port=30561/udp
    firewall-cmd --zone=public --permanent --add-port=30601/tcp
    firewall-cmd --reload
    firewall-cmd --list-all
    firewall-cmd --zone=public --permanent --remove-port=30560/tcp

Update nginx with a file under vhosts like this (/etc/nginx/vhosts/kafka-data-consistency.conf):

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
      # kdc.ccc.maxant.ch (confluent control center)
      # ############################################################

      server {
        listen 80;

        server_name kdc.ccc.maxant.ch;
        location / {
            proxy_pass http://localhost:30500/;
        }
      }

      # ############################################################
      # portainer.maxant.ch - dashboard
      # ############################################################
      server {
        listen 80;

        server_name portainer.maxant.ch;
        location / {
            proxy_pass http://localhost:29999/;
        }
      }

      # ############################################################
      # kdc.kafdrop.maxant.ch - kafka dashboard
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.kafdrop.maxant.ch;
        location / {
            proxy_pass http://localhost:30060/;
        }
      }

      # ############################################################
      # kdc.schemaregistry.maxant.ch
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.schemaregistry.maxant.ch;
        location / {
            proxy_pass http://localhost:30550/;
        }
      }

      # ############################################################
      # kdc.schemaregistry-ui.maxant.ch
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.schemaregistry-ui.maxant.ch;
        location / {
            proxy_pass http://localhost:30555/;
        }
      }

      # ############################################################
      # kdc.kafka-rest-proxy.maxant.ch
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.kafka-rest-proxy.maxant.ch;
        location / {
            proxy_pass http://localhost:30030/;
        }
      }

      # ############################################################
      # kdc.prometheus.maxant.ch
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.prometheus.maxant.ch;
        location / {
            proxy_pass http://localhost:29996/;
        }
      }

      # ############################################################
      # kdc.grafana.maxant.ch
      # ############################################################

      server {
        listen 80;
    
        server_name kdc.grafana.maxant.ch;
        location / {
            proxy_pass http://localhost:29993/;
        }
      }

      # ############################################################
      # kdc.jaeger.maxant.ch
      # ############################################################

      server { listen 80; server_name kdc.jaeger.maxant.ch; location / { proxy_pass http://localhost:30570/; } }

      # ############################################################
      # mf-*.maxant.ch
      # ############################################################

      # server { listen 80; server_name kdc.objects.maxant.ch; location / { proxy_pass http://localhost:30601/; } }

      # https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
      server { listen 80; server_name mf-contracts.maxant.ch;    proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8080/; } }
      server { listen 80; server_name mf-pricing.maxant.ch;      proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8081/; } }
      server { listen 80; server_name mf-web.maxant.ch;          proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8082/; } }
      server { listen 80; server_name mf-partners.maxant.ch;     proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8083/; } }
      server { listen 80; server_name mf-cases.maxant.ch;        proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8084/; } }
      server { listen 80; server_name mf-waitingroom.maxant.ch;  proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8085/; } }
      server { listen 80; server_name mf-organisation.maxant.ch; proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8086/; } }
      server { listen 80; server_name mf-billing.maxant.ch;      proxy_http_version 1.1; proxy_set_header Connection ""; location / { proxy_pass http://localhost:8087/; } }

      server {
        listen 80;
        server_name dockerstats.maxant.ch;
        location / {
            proxy_pass http://localhost:9323/;
        }
      }

      # ############################################################
      # ksql.maxant.ch - to enable cors
      # ############################################################
      server {
        listen 80;
        server_name ksql.maxant.ch;
      
            # https://enable-cors.org/server_nginx.html
            #location / {
            # if ($request_method = 'OPTIONS') {
            #    add_header 'Access-Control-Allow-Origin' '*';
            #    add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS';
            #    #
            #    # Custom headers and headers various browsers *should* be OK with but aren't
            #    #
            #    add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range';
            #    #
            #    # Tell client that this pre-flight info is valid for 20 days
            #    #
            #    add_header 'Access-Control-Max-Age' 1728000;
            #    add_header 'Content-Type' 'text/plain; charset=utf-8';
            #    add_header 'Content-Length' 0;
            #    return 204;
            # }
            # if ($request_method = 'POST') {
            #    add_header 'Access-Control-Allow-Origin' '*';
            #    add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS';
            #    add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range';
            #    add_header 'Access-Control-Expose-Headers' 'Content-Length,Content-Range';
            # }
            # if ($request_method = 'GET') {
            #    add_header 'Access-Control-Allow-Origin' '*';
            #    add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS';
            #    add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range';
            #    add_header 'Access-Control-Expose-Headers' 'Content-Length,Content-Range';
            # }
            # proxy_pass http://localhost:30401/;
            #}
        
            location /home  {
                               add_header 'Content-Type' 'text/html';
                               alias /tempi/ksql-app.html;
            }
            location /query {    
                               proxy_read_timeout 3600s; # an hour => after that the browser is probably gone, or it needs to reopen the request
                               proxy_pass http://localhost:30401/query/;
            }
      }

Restart nginx:

    sudo systemctl restart nginx

Create topics:

    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic claim-create-db-command
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic claim-create-search-command
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic graph-create-command
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic task-create-command
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic location-create-command
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic claim-created-event
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic task-created-event
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic partner-created-event
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic partner-created-anonymous
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic partner-created-german-speaking-anonymous-with-age
    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic partner-created-german-speaking-global-count
    kafka_2.12-2.7.0/bin/kafka-topics.sh --list --zookeeper maxant.ch:30000

If you have to delete topics, do it like this:

    kafka_2.12-2.7.0/bin/kafka-topics.sh --zookeeper maxant.ch:30000 --delete --topic claim-create-relationship-command

Create elasticsearch indexes:

    # check existing:
    curl -X GET "kdc.elasticsearch.maxant.ch/claims"
    curl -X GET "kdc.elasticsearch.maxant.ch/partners"

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

Create the databases in MySql:

    
    # mysql mysql -h maxant.ch --port 30300 -u root -psecret -e "CREATE DATABASE claims CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE claims CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE contracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfcontracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfpricing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfoutput CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfcases CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfbilling CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfdiscounts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfpartners CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p -e "CREATE DATABASE mfaddinfo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p
    FAILS - use IP!! docker run -it --rm mysql mysql -h zeus.com --port 30300 -u root -p

Set Java to version 8, because of Payara! (https://blog.payara.fish/java-11-support-in-payara-server-coming-soon)

    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/

E.g. run task service locally, but connecting to maxant.ch:

    # web:
    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
    $JAVA_HOME/bin/java -Xmx256M -Xms256M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -javaagent:elastic-apm-agent-1.6.1.jar \
             -Delastic.apm.service_name=web \
             -Delastic.apm.server_urls=http://maxant.ch:30200 -Delastic.apm.secret_token= \
             -Delastic.apm.application_packages=ch.maxant \
         -jar web/target/web-microbundle.jar \
         --port 8080 &

    # claims:
    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
    export MYSQL_HOST=maxant.ch
    export MYSQL_PORT=30300
    export MYSQL_USER=root
    export MYSQL_PASSWORD=secret
    $JAVA_HOME/bin/java -Xmx256M -Xms256M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8788 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -Delasticsearch.baseUrl=kdc.elasticsearch.maxant.ch \
         -Dtasks.baseurl=http://localhost:8082/tasks/rest/ \
         -javaagent:elastic-apm-agent-1.6.1.jar \
             -Delastic.apm.service_name=claims \
             -Delastic.apm.server_urls=http://maxant.ch:30200 -Delastic.apm.secret_token= \
             -Delastic.apm.application_packages=ch.maxant \
         -jar claims/target/claims-microbundle.jar \
         --port 8081 &

    # tasks:
    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
    $JAVA_HOME/bin/java -Xmx256M -Xms256M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8789 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -javaagent:elastic-apm-agent-1.6.1.jar \
             -Delastic.apm.service_name=tasks \
             -Delastic.apm.server_urls=http://maxant.ch:30200 -Delastic.apm.secret_token= \
             -Delastic.apm.application_packages=ch.maxant \
         -jar tasks/target/tasks-microbundle.jar \
         --port 8082 &

    # locations:
    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
    $JAVA_HOME/bin/java -Xmx256M -Xms256M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8791 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -javaagent:elastic-apm-agent-1.6.1.jar \
             -Delastic.apm.service_name=locations \
             -Delastic.apm.server_urls=http://maxant.ch:30200 -Delastic.apm.secret_token= \
             -Delastic.apm.application_packages=ch.maxant \
         -jar locations/target/locations-microbundle.jar \
         --port 8084 &

    # graphs:
    export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
    export NEO4J_HOST=kdc.neo4j.maxant.ch
    export NEO4J_PORT=30101
    export NEO4J_USER=a
    export NEO4J_PASSWORD=a
    $JAVA_HOME/bin/java -Xmx256M -Xms256M \
         -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8792 \
         -Dkafka.bootstrap.servers=maxant.ch:30001,maxant.ch:30002 \
         -Delasticsearch.baseUrl=kdc.elasticsearch.maxant.ch \
         -javaagent:elastic-apm-agent-1.6.1.jar \
             -Delastic.apm.service_name=graphs \
             -Delastic.apm.server_urls=http://maxant.ch:30200 -Delastic.apm.secret_token= \
             -Delastic.apm.application_packages=ch.maxant \
         -jar graphs/target/graphs-microbundle.jar \
         --port 8085 &

Start the UI:

    cd ui
    node node_modules/http-server/bin/http-server -p 8083 &

## Quarkus / GraalVM

Generate a project:

    mvn io.quarkus:quarkus-maven-plugin:0.21.1:create -DprojectGroupId=ch.maxant.kdc -DprojectArtifactId=webq -DclassName="ch.maxant.kdc.webq.RestResource" -Dpath="/webq"

Compile in dev mode (profit from hot-reload - no need to compile in IntelliJ!!):

    ./mvnw compile quarkus:dev

Package a runner:

    ./mvnw package

Run it:

    java -jar target/webq-1.0-SNAPSHOT-runner.jar 

Note that this jar has a dependency on webq-....jar, and also on the target/lib folder => copy everything relatively when running outside of the maven module folder. E.g.:

    mkdir /tmp/quarkus
    cp -R target/lib /tmp/quarkus/lib
    cp -R target/webq-1.0-SNAPSHOT*jar /tmp/quarkus/
    cd /tmp/quarkus/
    java -jar webq-1.0-SNAPSHOT-runner.jar 


Build a native executable: install Graal from https://github.com/oracle/graal/releases/download/vm-19.2.0/graalvm-ce-linux-amd64-19.2.0.tar.gz

    export GRAALVM_HOME=/shared/graal/graalvm-ce-19.2.0/

Install `native-image` and dependencies:

    /shared/graal/graalvm-ce-19.2.0/bin/gu install native-image

    sudo apt-get install build-essential libz-dev

Compile the native image:

    ./mvnw clean compile package -Pnative

Run it:

    ./target/webq-1.0-SNAPSHOT-runner

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

# Useful Kafka stuff:

Read from a topic:

    kafka_2.12-2.7.0/bin/kafka-console-consumer.sh --bootstrap-servers maxant.ch:30001 --topic ksql-test-cud-partners --from-beginning

Create a topic:

    kafka_2.12-2.7.0/bin/kafka-topics.sh --create --zookeeper maxant.ch:30000 --replication-factor 2 --partitions 4 --topic ksql-test-cud-partners

    kafka_2.12-2.7.0/bin/kafka-topics.sh --list --zookeeper maxant.ch:30000

Write some test data to a topic:

    kafka_2.12-2.7.0/bin/kafka-console-producer.sh --broker-list maxant.ch:30001,maxant.ch:30002 --topic ksql-test-cud-partners

Delete a topic:

    kafka_2.12-2.7.0/bin/kafka-topics.sh --zookeeper maxant.ch:30000 --delete --topic myTopic

# KSQL - DEPRECATED - See ksqlDB

Run the partner generator: `PartnerGenerator#main` => it generates new random partner data, a record every few seconds. 

Run a Kafka Stream `FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream#main`, which does what it says on the tin, using the generated partner data!
Note that you can start multiple instances of this. If you add another, there is a rebalancing and local state is sent over to
the new consumer and no longer reported by the original one.

You then have the basis for the queries which are created in this chapter.

After installation of ksql-server-1 into docker as documented above, we can test that it's running via it's REST API:

    curl -s "http://maxant.ch:30401/info" | jq '.'

Run ksql-cli locally:

    docker run -it --rm --name ksql-cli confluentinc/cp-ksql-cli:5.3.0

Then connect to the ksql-server:

    server http://maxant.ch:30401

And play around a little:

    show topics;
    print 'partner-created-event';

Create a streams:

    #all partners, as a stream:
    curl -X POST "http://maxant.ch:30401/ksql" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "CREATE STREAM s_partner (id VARCHAR, firstname VARCHAR, lastname VARCHAR, dateOfBirth VARCHAR, nationality INTEGER) WITH (KAFKA_TOPIC = '\''partner-created-event'\'', VALUE_FORMAT='\''JSON'\'', KEY = '\''id'\'');",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

    # all german speaking anonymised partners with age, as a stream:
    curl -X POST "http://maxant.ch:30401/ksql" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "CREATE STREAM s_partner_germanspeaking_anon_age (id VARCHAR, firstname VARCHAR, lastname VARCHAR, dateOfBirth VARCHAR, nationality INTEGER, age INTEGER) WITH (KAFKA_TOPIC = '\''partners-created-german-speaking-anonymous-with-age'\'', VALUE_FORMAT='\''JSON'\'', KEY = '\''id'\'');",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

Average age of customers in a hopping window (windows requires a stream rather than a table as their basis!):

    select sum(age)/count(*) from s_partner_germanspeaking_anon_age WINDOW HOPPING (SIZE 120 SECONDS, ADVANCE BY 60 SECONDS) group by 1;

    # average age of new partners per country in the last two minutes, hopping by minute (returns two rows per country)
    select nationality, count(*) as number, sum(age)/count(*) from s_partner_germanspeaking_anon_age WINDOW HOPPING (SIZE 120 SECONDS, ADVANCE BY 60 SECONDS) group by nationality;

    # average age of new partners per country, for each minute
    select nationality, count(*) as number, sum(age)/count(*) from s_partner_germanspeaking_anon_age WINDOW TUMBLING (SIZE 60 SECONDS) group by nationality;

Create tables:

    # a table of raw partners:
    curl -X POST "http://maxant.ch:30401/ksql" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "CREATE TABLE t_partner (id VARCHAR, firstname VARCHAR, lastname VARCHAR, dateOfBirth VARCHAR, nationality INTEGER) WITH (KAFKA_TOPIC = '\''partner-created-event'\'', VALUE_FORMAT='\''JSON'\'', KEY = '\''id'\'');",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

    # a table of german speaking anonymised parters with age:
    curl -X POST "http://maxant.ch:30401/ksql" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "CREATE TABLE t_partner_germanspeaking_anon_age (id VARCHAR, firstname VARCHAR, lastname VARCHAR, dateOfBirth VARCHAR, nationality INTEGER, age INTEGER) WITH (KAFKA_TOPIC = '\''partners-created-german-speaking-anonymous-with-age'\'', VALUE_FORMAT='\''JSON'\'', KEY = '\''id'\'');",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

    # count of new partners, per 5 min window:
    curl -X POST "http://maxant.ch:30401/ksql" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "CREATE TABLE t_new_partners_per_5mins AS SELECT nationality, COUNT(*) AS event_count FROM s_partner_germanspeaking_anon_age WINDOW TUMBLING (SIZE 5 MINUTES) GROUP BY nationality;",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

    # now we can select this data:
    curl -X POST "http://maxant.ch:30401/query" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "SELECT TIMESTAMPTOSTRING(ROWTIME, '\''yyyy-MM-dd HH:mm:ss.SSS'\''), nationality, event_count FROM t_new_partners_per_5mins;",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "latest"
      }
    }'

It outputs a new line every few seconds if there is no new data. 
It outputs a new row each time a new record is processed.
The record contains the timestamp, so that we can work out which window we are talking about (in this case 22:30-22:35).
It contains the country code, eg. 276, which had a count of 8, then a count of 9. Then a row came in for country code 
756 with a count of 5. So we can add the count.

    {"row":{"columns":["2019-07-24 22:32:01.891",276,8]},"errorMessage":null,"finalMessage":null,"terminal":false}

    {"row":{"columns":["2019-07-24 22:32:05.153",276,9]},"errorMessage":null,"finalMessage":null,"terminal":false}
    
    
    {"row":{"columns":["2019-07-24 22:32:07.834",756,5]},"errorMessage":null,"finalMessage":null,"terminal":false}
    
    {"row":{"columns":["2019-07-24 22:34:34.190",276,17]},"errorMessage":null,"finalMessage":null,"terminal":false}



    {"row":{"columns":["2019-07-24 22:34:43.019",756,12]},"errorMessage":null,"finalMessage":null,"terminal":false}

    {"row":{"columns":["2019-07-24 22:34:53.200",40,14]},"errorMessage":null,"finalMessage":null,"terminal":false}


    {"row":{"columns":["2019-07-24 22:35:06.644",40,1]},"errorMessage":null,"finalMessage":null,"terminal":false}

Notice how the count is reset to 1, when a new record arrives in the new window (22:35-22:40).

So we could well use this data to draw a graph with live updates, in a UI.

## Terminate a running query

    ksql> describe extended t_young_partners;
    
    Name                 : T_YOUNG_PARTNERS
    ...
    Queries that write into this TABLE
    -----------------------------------
    CTAS_T_YOUNG_PARTNERS_0 : CREATE TABLE T_YOUNG_PARTNERS WITH (REPLICAS = 2, PARTITIONS = 4, KAFKA_TOPIC = 'T_YOUNG_PARTNERS') AS SELECT *
    FROM T_PARTNER T_PARTNER
    WHERE (T_PARTNER.DATEOFBIRTH >= '2000-01-01');
    ...

Notice `CTAS_T_YOUNG_PARTNERS_0` => this is used for terminating the running query:
    
    ksql> terminate CTAS_T_YOUNG_PARTNERS_0;
    
     Message           
    -------------------
     Query terminated. 
    -------------------

## More KSQL stuff:

Go back to ksql-cli and:

    show tables;
    describe partners;
    select * from t_partner; // seems to follow and only print when records arrive
    SELECT ROWTIME, TIMESTAMPTOSTRING(ROWTIME, 'yyyy-MM-dd HH:mm:ss.SSS'), firstname, nationality, dateOfBirth from t_partner;
    show queries;
    describe extended t_partner;
    list properties;
    SET 'auto.offset.reset'='earliest'; // so that select works from start of topic/partition

    # average partner ages -> every time a record arrives, the result row is outputted for that record, eg. with the last example the new count and average age is outputted for the country of the record that was just processed.
    select sum(age)/count(*) from t_partner_germanspeaking_anon_age group by 1;
    select nationality, sum(age)/count(*) from t_partner_germanspeaking_anon_age group by nationality;
    select nationality, count(*) as number, sum(age)/count(*) from t_partner_germanspeaking_anon_age group by nationality;

You can query via REST like this (could be useful for an event store?? not really...):

    curl -X POST "http://maxant.ch:30401/query" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "select * from s_partner where id = '\''9d601f00-47a7-446b-8bd6-e3843f0a7273'\'';",
      "streamsProperties": {
        "ksql.streams.auto.offset.reset": "earliest"
      }
    }'

But this too continues to respond with data. It isn't designed to give a snapshot rather it's designed to give a stream of data.

Notice the special escaping of single quotes within the single quoted body with `'\''`, ie. the quote is escaped and also within quotes!

NOTE: you can add `limit 1` to the end of hte quey and if you select from a table, you get the one record based on the ID that you are selecting.
Useful if you are selecting by ID, otherwise useless.

### Notes on KSQL:

- Table uses upsert semantics (CUD) vs. stream which is just a long list of records.
- Default persistent storage for Streams: https://github.com/facebook/rocksdb
- `InvalidStateStoreException: The state store, CountByPartnerCountryStoreName, may have migrated to another instance.`
  - this seems to happen if I have three keys in my store, but I run 4 instances of the stream application.

### TODO KSQL:

- persistency? what happens when ksql server instance goes down? the data is persistent according to the topic - it is still there.
- what happens if kafka stream dies? it continues from the index from which it left off when it crashed.
- how to create a global table using rest? its possible with kafka stream API...
- article on unioning data from other ksql-server nodes in the cluster?
  - https://docs.confluent.io/current/streams/concepts.html#interactive-queries
  - https://docs.confluent.io/current/streams/developer-guide/interactive-queries.html#querying-remote-state-stores-for-the-entire-app
- creating indexes on tables to improve performance?
- select * from beginning => must set `ksql.streams.auto.offset.reset` (web) or `auto.offset.reset` (ksql-cli) to the value `earliest`
  - it is NOT fast! see below
- KSQL builds on top of streams builds on top of Kafka
  - a ksql-server runs multiple kafka stream applications (each ksql is a stream app).
  - it isn't possible to create a global ktable with ksql.
  - queries are passed from ksql-server to ksql-server over a command topic
  - queries running on a ksql-server are persistent and just subscribe to a subset of partitions
  - queries running in the CLI are not passed around, but run locally on that server with their own app Id and so 
  - they subscribe to all topics
  - `print mytable` appears to be the equivalent of `select * from mytable` i.e. a transient query which subscribes to
    all partitions. i say that because if i do that on both ksql-server instances, I see the same events on both CLIs.
  - see https://stackoverflow.com/questions/57333738 for more information about the above statements. 
    
- seems REALLY REALLY slow: https://stackoverflow.com/questions/57334056/why-is-ksql-taking-so-long-to-respond
  - Based on the information gained from https://stackoverflow.com/questions/57333738/ it could make sense that 
    it takes so long to respond coz it takes time for the ksql-server to start the "kafka stream application" 
    which runs the query. That is a lot of overhead just to read a single record as done above. One could 
    conclude that using a ksql-server and it's REST API to build something like an (event) store which you can 
    query for records relating to a specific ID doesn't make sense. It makes more sense to use ksql to manipulate 
    data and then subscribe to an output topic instead of using REST.
- indexes in KSQL? https://stackoverflow.com/questions/57334096 => no secondary indexes. you could create a topic for each key that you want... does that make sense?
- TODO how can we add several ksql servers to ccc?
- contents of global ktable doesnt appear to be updated immediately:

    join country count 11 into the partner from 276
    join country count 11 into the partner from 276
    join country count 11 into the partner from 276
    join country count 11 into the partner from 276
    join country count 15 into the partner from 276
    join country count 15 into the partner from 276
 
That is output of a single stream application (no others were running at the time).
The above should have consecutive counts, because the count is increased every time a record is processed!
As you can see, it is suddenly increased from 11 to 15. Note that the load was low - those lines were output over several seconds.

## KSQL Web Client

- See nginx config, esp. the timeout


    CREATE TABLE T_YOUNG_PARTNERS WITH (REPLICAS = 2, PARTITIONS = 4, KAFKA_TOPIC = 'T_YOUNG_PARTNERS') AS SELECT *
    FROM T_PARTNER T_PARTNER
    WHERE (T_PARTNER.DATEOFBIRTH >= '1990-01-01');

Javascript: 

    var body = {"ksql": "SELECT * FROM t_young_partners;","streamsProperties": {"ksql.streams.auto.offset.reset": "earliest"}};
    function go() {
        window.responses = "";
        var xhttp = new XMLHttpRequest();
        xhttp.onreadystatechange = function() {
            if (this.readyState == 3 && this.status == 200) {
                window.responses += this.responseText;
                console.log("got more data...");
                this.responseText.split('\n').forEach(r => {
                    if(r && r.trim()) {
                        try {
                            console.log(JSON.parse(r));
                        } catch(e) {
                            console.error(e);
                        }
                    }
                });
            }
        };
        xhttp.open("POST", "/query", true);
        xhttp.setRequestHeader('Content-Type', 'application/vnd.ksql.v1+json');
        xhttp.send(JSON.stringify(body));
    }
    go();


## Links:

- Installation: https://docs.confluent.io/current/ksql/docs/installation/install-ksql-with-docker.html
- Concepts: https://docs.confluent.io/current/streams/concepts.html => Global Table and info that KTable only contains data for partition. TODO where is the page that talks about manually fetching data from other ksql nodes?
- Tutorials: https://docs.confluent.io/current/ksql/docs/tutorials/
- Docs: https://docs.confluent.io/current/ksql/docs/index.html
- REST API: https://docs.confluent.io/current/ksql/docs/developer-guide/api.html
- Kafka Connect - JDBC Deep Dive: https://www.confluent.io/blog/kafka-connect-deep-dive-jdbc-source-connector
- Cook Book: https://www.confluent.io/product/ksql/stream-processing-cookbook
- Example docker compose content: https://github.com/confluentinc/cp-docker-images/blob/5.3.0-post/examples/cp-all-in-one/docker-compose.yml

# ksqlDB

## Links

- https://docs.ksqldb.io/en/latest/tutorials/embedded-connect/
- https://www.confluent.io/blog/kafka-connect-deep-dive-jdbc-source-connector/
- https://docs.confluent.io/5.4.1/connect/kafka-connect-jdbc/source-connector/index.html

## Example

Install the jdbc connector:

    docker run --rm -v $PWD/confluent-hub-components:/share/confluent-hub-components confluentinc/ksqldb-server:0.8.1 confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:5.4.1
    docker run -v $PWD/confluent-hub-components:/share/confluent-hub-components confluentinc/ksqldb-server:0.8.1 confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:5.4.1

And add the mysql client jar:

    wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-8.0.19.zip
    mkdir tmp
    mv mysql-connector-java-8.0.19.zip tmp
    cd tmp
    unzip mysql-connector-java-8.0.19.zip
    mv mysql-connector-java-8.0.19/mysql-connector-java-8.0.19.jar ../confluent-hub-components/confluentinc-kafka-connect-jdbc/lib/
    cd ..
    rm -rf tmp

then bounce ksqldb-server

    docker-compose -f dc-base.yml rm -fsv kdc-kafka-1
    docker-compose -f dc-base.yml up -d 

## create a schema:

https://www.confluent.io/blog/kafka-connect-deep-dive-jdbc-source-connector/

if you need to delete old schemas:

    curl -X DELETE http://kdc.schemaregistry.maxant.ch/subjects/mytopicname-value/versions/1

get existing versions using paths within this tree:

    curl -X GET http://kdc.schemaregistry.maxant.ch/subjects/
    curl -X GET http://kdc.schemaregistry.maxant.ch/subjects/mytopicname-value/versions
    curl -X GET http://kdc.schemaregistry.maxant.ch/subjects/mytopicname-value/versions/1

## create a table

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p

    use contracts;

    select * from merkmale order by mut desc;

connect to ksqldb-cli and create a connector:

    docker exec -it kdc-ksqldb-cli ksql http://maxant.ch:30410

    CREATE SOURCE CONNECTOR jdbc_source WITH (
      'connector.class'          = 'io.confluent.connect.jdbc.JdbcSourceConnector',
      'connection.url'           = 'jdbc:mysql://maxant.ch:30300/contracts',
      'connection.user'          = 'root',
      'connection.password'      = 'secret',
      'topic.prefix'             = 'jdbc_',
      'table.whitelist'          = 'merkmale',
      'mode'                     = 'timestamp',
      'timestamp.column.name'    = 'mut',
      'poll.interval.ms'         = 3000,
      'key'                      = 'CONTRACT_ID'
    );

Mode - see https://docs.confluent.io/current/connect/kafka-connect-jdbc/source-connector/source_config_options.html#mode
or https://github.com/confluentinc/kafka-connect-jdbc/blob/master/src/main/java/io/confluent/connect/jdbc/source/JdbcSourceConnectorConfig.java

Watch a topic:

    PRINT 'myTopicName';

Get all info off topic:

    PRINT 'myTopicName' FROM BEGINNING;

Create a stream to wrap the topic, as we need it as the basis for future stuff in KSQL:

    CREATE STREAM s_myTopicName (
        rowkey STRING KEY,
        contractNumber STRING
    )
    WITH (kafka_topic='myTopicName', value_format='JSON');

Read all the data on the stream:

    SET 'auto.offset.reset'='earliest';
    select * from s_myTopicName emit changes;
    ctrl+c
    SET 'auto.offset.reset'='latest';

Create a new stream, but note that we need a unique key in order to create tables on top of it.
Currently our stream doesn't have a **unique** key because we can have many events per contract number.
See also https://www.confluent.io/stream-processing-cookbook/ksql-recipes/creating-composite-key/
At the same time, let's filter on only interesting events. Note that we need to set the offset to earliest, so that
all data in the existing stream is consumed.
We also do this in two steps because it doesn't seem possible to create the composite key AND use it, in one statement.

    SET 'auto.offset.reset' = 'earliest';

    DROP STREAM IF EXISTS s_contractinstances_raw DELETE TOPIC;
    CREATE STREAM s_contractinstances_raw AS 
        SELECT *, 
               contract_number + '::' + CAST(mut AS STRING) AS COMPOSITE_KEY  -- creates a composite key
        FROM s_myTopicName
        WHERE someColumn = 'aSpecificValue';

    DROP STREAM IF EXISTS s_contractinstances DELETE TOPIC;
    CREATE STREAM s_contractinstances AS 
        SELECT *
        FROM s_contractinstances_raw
        PARTITION BY COMPOSITE_KEY;

Now we do the same for beginn/ablauf:

    SET 'auto.offset.reset' = 'earliest';

Now create a materialized contract view:

    DROP TABLE IF EXISTS v_contracts DELETE TOPIC;
    CREATE TABLE v_contracts AS
        SELECT
            latest_by_offset(contractNumber) as contractNumber,
            collect_list(merkmalswert) as werte
        FROM s_contracts
        GROUP BY CONTRACT_NUMBER
    EMIT CHANGES;

    select * from V_CONTRACTS where ROWKEY = '123456';

    curl -X POST "http://maxant.ch:30410/query" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "select * from V_CONTRACTS where ROWKEY = '\''123456'\'';"
    }' \
    | jq '.[1].row.columns[3]'

Or, in order to get the change timestamp for each of those, let's first create a map field, 
see https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#map:

    DROP STREAM IF EXISTS S_CONTRACTS2 DELETE TOPIC;
    CREATE STREAM S_CONTRACTS2 AS 
        SELECT *,
               map('val' := val, 'changed' := CAST(changed AS STRING) ) as changed_value
        FROM S_CONTRACTS_RAW
        WHERE ... = '...'
        PARTITION BY CONTRACT_NUMBER;

    DROP TABLE IF EXISTS V_CONTRACTS2 DELETE TOPIC;
    CREATE TABLE V_CONTRACTS2 AS
        SELECT
            latest_by_offset(CONTRACT_NUMBER) as CONTRACT_NUMBER,
            collect_list(CHANGED) as CHANGES
        FROM S_CONTRACTS2
        GROUP BY CONTRACT_NUMBER
    EMIT CHANGES;

=> fails with `Function 'collect_list' does not accept parameters (MAP<STRING, STRING>)` :-(

Let's try with json instead:

    DROP STREAM IF EXISTS S_CONTRACTS3 DELETE TOPIC;
    CREATE STREAM S_CONTRACTS3 AS 
        SELECT *,
               ('{"value":"' + ... + '", "changed":"' + CAST(changed AS STRING) + '"}' ) as changed_value
        FROM s_values_raw
        WHERE ... = '...'
        PARTITION BY CONTRACT_NUMBER;

    DROP TABLE IF EXISTS V_CONTRACTS3 DELETE TOPIC;
    CREATE TABLE V_CONTRACTS3 AS
        SELECT
            latest_by_offset(CONTRACT_NUMBER) as CONTRACT_NUMBER,
            collect_list(value_changed) as values
        FROM S_CONTRACTS3
        GROUP BY CONTRACT_NUMBER
    EMIT CHANGES;

Kinda works:

    curl -X POST "http://maxant.ch:30410/query" -H 'Content-Type: application/vnd.ksql.v1+json' -d '
    {
      "ksql": "select * from V_CONTRACTS3 where ROWKEY = '\''123456'\'';"
    }' | jq '.[1].row.columns[3][] | fromjson'

Returns json, although not quite in an array: 

    {
      "value": "A",
      "changed": "1586617501000"
    }
    {
      "value": "B",
      "changed": "1586617641000"
    }

    insert into values (valuename, value, contract_number) values ('a', 'b', '123456');
    update values set value = '2024-12-31', changed = now() where valuename = 'begin' and contract_number = '123456';
    
    select * from values order by changed desc;

TODO join streams to get a contract view

## ksqldb-cli

    docker exec -it kdc-ksqldb-cli ksql http://maxant.ch:30410

    show topics;
    show streams;
    show connectors;
    show queries;
    DESCRIBE CONNECTOR JDBC_SOURCE;


# Useful Elasticsearch stuff:

- http://elasticsearch-cheatsheet.jolicode.com/
- Info about indices

    curl -X GET "kdc.elasticsearch.maxant.ch/_cat/indices?v"

    health status index                              uuid                   pri rep docs.count docs.deleted store.size pri.store.size
    yellow open   claims                             -KxQ_roSTNW8IFwFGi6N2Q   1   1          0            0       230b           230b
    yellow open   apm-7.0.0-metric-2019.05.09        RQrz7bcTQhWUYW9GAgzYnw   1   1       2667            0    966.3kb        966.3kb
    yellow open   partners                           mxbLnKQgTIywF8D4OZ12aA   1   1          0            0       230b           230b
    yellow open   apm-7.0.0-error-2019.05.09         e5Qp8VTqQLyBP4o_DiuGlQ   1   1          3            0     57.4kb         57.4kb
    yellow open   apm-7.0.0-transaction-2019.05.09   vOQCq7SeTimzfOIshOofww   1   1        227            0    684.3kb        684.3kb
    yellow open   metricbeat-7.0.0-2019.05.08-000001 rUfDO3ekQ4G0VGzSauON9Q   1   1    1541549            0    546.9mb        546.9mb
    yellow open   apm-7.0.0-span-2019.05.09          6UIsLN4TT6Ov-MvAupV6iw   1   1        432            0      816kb          816kb
    yellow open   .kibana                            Y6bbGKqTT2eyJmDJE2cbzA   1   1          7           10    199.3kb        199.3kb
    yellow open   filebeat-7.0.0-2019.05.08-000001   tJCTsiL6QzqvPIfE6Lnnxg   1   1     940117            0      277mb          277mb

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

    curl -X GET "kdc.elasticsearch.maxant.ch/claims/_search?pretty" -H 'Content-Type: application/json' -d'{ "query" : { "regexp" : { "description" : ".*avion.*" } } }'

**NOTE that a regexp query does not use analyzers!!** - so a search for "m'avion" doesn't work!

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

    MATCH (n)-[r]-(m)
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

    MATCH (n)-[r]-(m)
    RETURN *

    CREATE CONSTRAINT ON (p:Partner) ASSERT p.id IS UNIQUE
    CREATE CONSTRAINT ON (c:Contract) ASSERT c.id IS UNIQUE
    CREATE CONSTRAINT ON (c:Claim) ASSERT c.id IS UNIQUE
    CREATE INDEX ON :Claim(date)

    MATCH (n)-[r]-(m)
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
- create an index for `claims*` using `date` as the time field
- create an index for `apm-*` using `@timestamp` as the time field
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

# Tracing (APM)

Chose to use elasticsearch's APM Server - it's opentracing according to opentracing.org
And it is well documented.
Not yet convinced that the microprofile supports full propagation as well as tracing jdbc etc.

- See https://www.elastic.co/guide/en/apm/server/current/running-on-docker.html
- Install APM Server into docker
- config located in elastic-apm-server.docker.yml which is imported in our custom docker build file.
- Once APM server is running in docker, go to kibana home page and setup APM: http://kdc.kibana.maxant.ch/app/kibana#/home/tutorial/apm?_g=()
- click the button "check apm server status"
- Download `elastic-apm-agent-1.6.1.jar` from https://search.maven.org/search?q=a:elastic-apm-agent and put it in the root folder of this project
- Add the following to the launch config:

         -javaagent:elastic-apm-agent-1.6.1.jar \
         -Delastic.apm.service_name=tasks \
         -Delastic.apm.server_urls=http://maxant.ch:30200 \
         -Delastic.apm.secret_token= \
         -Delastic.apm.application_packages=ch.maxant \

- start the app
- click the rest of the buttons in the kibana webapp
- view data: http://kdc.kibana.maxant.ch/app/apm#/services

Added to UI. Propagation now working thanks to span: https://discuss.elastic.co/t/transaction-not-automatically-propagated-from-browser-to-backend/179259

Links:

- Javascript Agent API: https://www.elastic.co/guide/en/apm/agent/rum-js/current/index.html
  - old: Javascript Agent API: https://www.elastic.co/guide/en/apm/agent/js-base/4.x/api.html
- Java Agent API: https://www.elastic.co/guide/en/apm/agent/java/current/public-api.html

# MySql

See https://hub.docker.com/_/mysql and https://blog.jpalardy.com/posts/throwaway-mysql-servers-with-docker/

    docker run --name mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -p 30300:3306 --rm mysql

Use empty password when prompted.

Or:
    docker run --name mysql -e MYSQL_ROOT_PASSWORD=secret -p 30300:3306 --rm mysql

Connecting to it:

    docker run -it --rm mysql mysql -h 172.17.0.2 -u root -p
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -p
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -psecret -e "CREATE DATABASE claims CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -psecret -e "CREATE DATABASE contracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

    not working, prolly coz its a mariadb client: -(  mysql --host 172.17.0.2 --port 3306 -u root -p

    # by default access from remote is enabled:
    select host, password_expired, password_last_changed, password_lifetime ,account_locked ,  Password_reuse_history ,Password_reuse_time ,Password_require_current from user where user = 'root';

# Cors Proxy

https://www.npmjs.com/package/local-cors-proxy

not used at the moment. but runs like this:

    node node_modules/local-cors-proxy/bin/lcp.js --proxyUrl=http://maxant.ch:30200 --port=30200

    curl -X "POST" -H "Content-Type: application/x-ndjson" -d @/tmp/body.txt http://localhost:30200/proxy/intake/v2/rum/events

# Favicon

Created using https://realfavicongenerator.net/

Added:

- ui/android-chrome-192x192.png
- ui/android-chrome-512x512.png
- ui/apple-touch-icon.png
- ui/browserconfig.xml
- ui/favicon-16x16.png
- ui/favicon-32x32.png
- ui/favicon.ico
- ui/mstile-144x144.png
- ui/mstile-150x150.png
- ui/mstile-310x150.png
- ui/mstile-310x310.png
- ui/mstile-70x70.png
- ui/safari-pinned-tab.svg
- ui/site.webmanifest

# Links

- Microprofile Specs: https://github.com/eclipse/microprofile-health/releases/tag/1.0
- Microprofile POMs, etc.: https://github.com/eclipse/microprofile
- Payara Docs: https://docs.payara.fish/documentation/microprofile/
- Payara Examples: https://github.com/payara/Payara-Examples/tree/master/microprofile

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

# Grafana

https://grafana.com/docs/grafana/latest/installation/configure-docker/

Add the following to `/etc/docker/daemon.json` config, in order to track docker:

    {
      "metrics-addr" : "127.0.0.1:9323",
      "experimental" : true
    }

Using bind mounts and resolving user problems:

    mkdir data # creates a folder for your data
    ID=$(id -u) # saves your user id in the ID variable
    
    # starts grafana with your user id and using the data folder
    docker run -d --user $ID --volume "$PWD/data:/var/lib/grafana" -p 3000:3000 grafana/grafana:5.1.0

