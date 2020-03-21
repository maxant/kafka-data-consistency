
## Install minikube

(version 1.8.2 didnt work on centos 7.7 => reverted to an older version)

    curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-1.7.2-0.x86_64.rpm
    rpm -ivh minikube-1.7.2-0.x86_64.rpm 
    /usr/bin/minikube start --vm-driver=kvm2 --alsologtostderr -v=8 --force

## Start Minikube and Dashboard

    minikube start --memory 8192 --cpus 4
    minikube addons enable metrics-server
    minikube dashboard &
    # paste the URL into nginx config and restart nginx
    # make sure you note the port, and then run this, replacing the `39309` from the output of the dashboard:
    socat TCP-LISTEN:40000,fork TCP:127.0.0.1:39309 &

After a while, remove evicted pods if necessary:

    kubectl -n kafka-data-consistency get pods | grep Evicted | awk '{print $1}' | xargs kubectl -n kafka-data-consistency delete pod

## Kubernetes

If necessary use the minikube docker host:

    eval $(minikube -p minikube docker-env)

Run `./build.sh` after getting Kafka (see above).

Create a namespace:

    kubectl create -f namespace.json

    # set default namespace for kubectl (note commands lower down still provide it, but now unneccesarily)
    kubectl config set-context --current --namespace=kafka-data-consistency

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
    kubectl -n kafka-data-consistency delete deployment mysql
    kubectl -n kafka-data-consistency delete service mysql
    kubectl -n kafka-data-consistency delete deployment ksql-server-1
    kubectl -n kafka-data-consistency delete service ksql-server-1
    kubectl -n kafka-data-consistency delete deployment ksql-server-2
    kubectl -n kafka-data-consistency delete service ksql-server-2
    kubectl -n kafka-data-consistency delete deployment confluent-control-center
    kubectl -n kafka-data-consistency delete service confluent-control-center

Create deployments and services:

    kubectl -n kafka-data-consistency apply -f zookeeper.yaml
    kubectl -n kafka-data-consistency apply -f kafka-1.yaml
    kubectl -n kafka-data-consistency apply -f kafka-2.yaml
    kubectl -n kafka-data-consistency apply -f elasticsearch.yaml
    kubectl -n kafka-data-consistency apply -f neo4j.yaml
    kubectl -n kafka-data-consistency apply -f kibana.yaml
    kubectl -n kafka-data-consistency apply -f elastic-apm-server.yaml
    kubectl -n kafka-data-consistency apply -f mysql.yaml
    kubectl -n kafka-data-consistency apply -f ksql-server-1.yaml
    kubectl -n kafka-data-consistency apply -f ksql-server-2.yaml
    kubectl -n kafka-data-consistency apply -f confluent-control-center.yaml

Setup forwarding like this (some are accessed directly from outside, others are accessed via nginx):

    # minikube port, see kibana metricbeat for kube way down below
    socat TCP-LISTEN:10250,fork TCP:$(minikube ip):10250 &

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

    # mysql
    socat TCP-LISTEN:30300,fork TCP:$(minikube ip):30300 &

    # ksql-server-1, ksql-server-2
    socat TCP-LISTEN:30401,fork TCP:$(minikube ip):30401 &
    socat TCP-LISTEN:30402,fork TCP:$(minikube ip):30402 &

    # confluent-control-center
    socat TCP-LISTEN:30500,fork TCP:$(minikube ip):30500 &

Useful Kube stuff:

    kubectl describe nodes

    # restart kube entirely, deleting everything
    minikube stop
    rm -rf ~/.minikube
    minikube delete
    minikube config set vm-driver kvm2
    minikube delete
    minikube start --memory 8192 --cpus 4
    # if there is an error above, like "cluster does not exist", or it doesnt do anything, then add `--force` at the start
    git clone https://github.com/kubernetes-incubator/metrics-server.git
    cd metrics-server/
    kubectl create -f deploy/1.8+/
    minikube addons enable metrics-server
    minikube dashboard &
    # make sure you note the port, and then run this, replacing the `39309` from the output of the dashboard:
    socat TCP-LISTEN:40000,fork TCP:127.0.0.1:39309 &

    # connect to the vm. eg. top and stuff to see whats going on inside.
    minikube ssh

    # create a deployment
    kubectl run hello-minikube --image=k8s.gcr.io/echoserver:1.10 --port=8080
    # create a service from a deployment
    kubectl expose deployment hello-minikube --type=NodePort

    #Delete evicted pods (after crashes):
    kubectl -n kafka-data-consistency get pods | grep Evicted | awk '{print $1}' | xargs kubectl -n kafka-data-consistency delete pod

    completely remove minikube: https://gist.github.com/robinkraft/a0987b50de8b45e4bdc907d841db8f23

TODO

- kube secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- minikube volumes:
  - https://stackoverflow.com/questions/42456159/minikube-volumes
  - https://github.com/kubernetes/minikube/blob/master/docs/persistent_volumes.md
- kubernetes/prometheus/grafana: https://raw.githubusercontent.com/giantswarm/kubernetes-prometheus/master/manifests-all.yaml

# K3S

See https://k3s.io/
See https://rancher.com/docs/k3s/latest/en/cluster-access/

    curl -sfL https://get.k3s.io | sh -

    sudo systemctl status k3s

    k3s kubectl help
    k3s kubectl --all-namespaces get pods
    k3s kubectl get services --namespace kafka-data-consistency
    k3s kubectl --namespace kafka-data-consistency explain pods
    k3s kubectl --namespace kafka-data-consistency get pods

Get password for accessing https://localhost:6443:

    cat /etc/rancher/k3s/k3s.yaml

Didnt work because of problem with no xfs file system on centos 7.

    /usr/local/bin/k3s-uninstall.sh

See https://github.com/rancher/k3s/issues/495

Future: https://kauri.io/37-install-and-configure-a-kubernetes-cluster-with/418b3bc1e0544fbc955a4bbba6fff8a9/a

# Kind

See https://kind.sigs.k8s.io/docs/user/quick-start/


create a cluster (with debug logging, to see any errors in detail):

    ./kind create cluster --name kdc-dev -v 99

delete a cluster:

    ./kind delete cluster --name kdc-dev

Failed due to not being able to pull some images. no info found on google. might be related to centos7 and not having the right file system?

# microk8s

Needs Snap, which needs Centos 7.6 or higher.

    cat /etc/centos-release

https://snapcraft.io/install/microk8s/centos

    yum install epel-release

    yum install snapd
    systemctl enable --now snapd.socket
    ln -s /var/lib/snapd/snap /snap
    reboot

    snap install microk8s --classic --channel=1.17/stable
    usermod -a -G microk8s $USER
    #re-enter the session
    su - $USER

    #inspect microk8s:
    microk8s.inspect

it told me to create `/etc/docker/daemon.json` with this content:

    {
        "insecure-registries" : ["localhost:32000"] 
    }

then restart docker with `systemctl restart docker`

the inspection command also creates a tarball with all kinds of stuff like logs in it.


    microk8s.status
    microk8s.status --wait-ready



microk8s.kubectl get nodes
microk8s.kubectl get services


If RBAC is not enabled access the dashboard using the default token retrieved with:

token=$(microk8s.kubectl -n kube-system get secret | grep default-token | cut -d " " -f1)
microk8s.kubectl -n kube-system describe secret $token

In an RBAC enabled setup (microk8s.enable RBAC) you need to create a user with restricted
permissions as shown in:
https://github.com/kubernetes/dashboard/blob/master/docs/user/access-control/creating-sample-user.md

microk8s also failed to work on centos7. maybe also due to xfs. 
cannot change xfs partitions sizes, so not able to make any new volumes. wait for centos8...

# minikube

cant get that to work now either

# kvm alpine for k3s

osinfo-query os
# that supports alpine 3.8





