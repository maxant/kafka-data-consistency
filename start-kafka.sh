#########################################################
# appends properties to end of config file
# and then starts kafka as normal
#########################################################

env

file=/kafka/config/server.properties

#new lines at end of file
echo  "" >>                 $file
echo  "" >>                 $file
echo  "# MAXANT CONFIG:" >> $file

#echo $ZOOKEEPER_HOST zookeeper >> /etc/hosts
echo zookeeper.connect=$ZOOKEEPER_HOST_PORT >> $file
echo broker.id=$ID >>                          $file

ip=$(hostname -I)
#need to trim space at end of ip :-(
#doesnt seem to be an issue when building on centos, which is weird! => ip=${ip::-1}
echo listeners=PLAINTEXT://${ip%?}:9092 >>            $file

#have to set advertised.listeners for hosts that are no in same network. but kafka seems to use this address when connecting brokers. 
echo "own ip address: ::$ip::"
echo advertised.listeners=PLAINTEXT://$ADVERTISED_LISTENER_HOST_PORT >> $file

echo "log4j.rootLogger=DEBUG, stdout, kafkaAppender" >> /kafka/config/log4j.properties



# if you need to use the docker host, you could do it like this:
#echo zookeeper.connect=$DOCKER_HOST:2181 >>    $file

cat /kafka/config/server.properties

export KAFKA_HEAP_OPTS="-Xmx256M -Xms256M"

./bin/kafka-server-start.sh $file
