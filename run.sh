#########################################################
# runs images. ensure you call build.sh first!
#########################################################
#
# NOTE: no volumes are used, so restarting all images
#       causes data and topic loss!
#########################################################
#
# TODO at the moment, if you want to change the number
#      of brokers, you need to do it here and down below
#      it would be nice to be able to set a param just once
#########################################################

#########################################################
# stop kafka brokers
for id in {1..2}
do
    p=909$id:9092

    if [ `docker ps --all | grep kafka_$id | wc -l` -ge 1 ]
      then
        if [[ "`docker inspect -f {{.State.Status}} kafka_$id`" == "running" ]]
          then
            echo killing kafka_$id...
            docker kill kafka_$id
        else
            echo kafka_$id not running
        fi
        echo removing kafka_$id container...
        docker rm kafka_$id
    else
        echo kafka_$id not found
    fi

done


#########################################################
# zookeeper

if [ `docker ps --all | grep zookeeper | wc -l` -ge 1 ]
  then
    if [[ "`docker inspect -f {{.State.Status}} zookeeper`" == "running" ]]
      then
        echo killing zookeeper...
        docker kill zookeeper
    else
        echo zookeeper not running
    fi
    echo removing zookeeper container...
    docker rm zookeeper
else
    echo zookeeper not found
fi

echo starting zookeeper...
docker run --name zookeeper -p 2181:2181 maxant/zookeeper >/dev/null 2>&1 &

#"INFO binding to port 0.0.0.0/0.0.0.0:2181"
counter=0
until [ $counter -ge 1 ]
do
    echo waiting for zookeeper...
    sleep 0.5;
    counter=`docker logs zookeeper | grep binding | wc -l`
done

zookeeperhost=$(docker inspect -f {{.NetworkSettings.IPAddress}} zookeeper)
echo zookeeper is up: $zookeeperhost

#########################################################
# get ip address from docker0 interface and grep just the ip address
dockerhost=$(ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+')
echo docker host is: $dockerhost

#########################################################
# start kafka brokers

kafkahosts=
for id in {1..2}
do
    p=909$id:9092

    echo starting kafka_$id...

    docker run --name kafka_$id -p $p -e "ID=$id" -e "DOCKER_HOST=$dockerhost" -e "ZOOKEEPER_HOST=$zookeeperhost" maxant/kafka >/dev/null 2>&1 &

    sleep 0.5;

    kafkahost=$(docker inspect -f {{.NetworkSettings.IPAddress}} kafka_$id)
    echo kafka_$id is up: $kafkahost
    kafkahosts=$kafkahost,$kafkahosts
done


counter=0
until [ $counter -ge 1 ]
do
    echo waiting for kafka_1...
    sleep 0.5;
    counter=`docker logs kafka_1 | grep "started (kafka.server.KafkaServer)" | wc -l`
done

echo creating topic
kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 2 --partitions 4 --topic my-topic
echo topic created
echo ""
echo COMPLETED. Kafka boostrap servers: $kafkahosts

#
# TODO
#
# mvn clean install && java -Ddefault.property=asdf -jar web/target/web-microbundle.jar
#   => put build part into build, and leave run part here
# wget http://127.0.0.1:8080/web/rest/test
#