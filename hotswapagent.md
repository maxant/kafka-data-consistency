# HotswapAgent + OpenJDK 11

- openjdk 11
  - https://openjdk.java.net/install/index.html
    - https://jdk.java.net/11/

    tar xvf openjdk-11.0.2_linux-x64_bin.tar.gz

- move it to `/usr/lib/jvm/jdk-11.0.2` => but leave it without alternatives, as we dont want to use it as default yet

    sudo cp -R jdk-11.0.2/ jdk-11.0.2-dcevm

- https://github.com/TravaOpenJDK/trava-jdk-11-dcevm
  - https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases
    - https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases/download/dcevm-11.0.1%2B8/java11-openjdk-dcevm-linux.tar.gz

    cd /usr/lib/jvm
    sudo cp ~/Downloads/java11-openjdk-dcevm-linux.tar.gz .
    sudo tar xvf java11-openjdk-dcevm-linux.tar.gz
    sudo rm -rf java11-openjdk-dcevm-linux.tar.gz
    dcevm-11.0.1+8/bin/java -version

Outputs:

    Starting HotswapAgent '/usr/lib/jvm/dcevm-11.0.1+8/lib/hotswap/hotswap-agent.jar'
    HOTSWAP AGENT: 21:37:10.768 INFO (org.hotswap.agent.HotswapAgent) - Loading Hotswap agent {1.3.1-SNAPSHOT} - unlimited runtime class redefinition.
    HOTSWAP AGENT: 21:37:11.202 INFO (org.hotswap.agent.config.PluginRegistry) - Discovered plugins: [Hotswapper, JdkPlugin, WatchResources, ClassInitPlugin, AnonymousClassPatch, Hibernate, Hibernate3JPA, Hibernate3, Spring, Jersey1, Jersey2, Jetty, Tomcat, ZK, Logback, Log4j2, MyFaces, Mojarra, Omnifaces, Seam, ELResolver, WildFlyELResolver, OsgiEquinox, Owb, Proxy, WebObjects, Weld, JBossModules, ResteasyRegistry, Deltaspike, GlassFish, Vaadin, Wicket]
    openjdk version "11.0.1.6" 2018-12-16
    OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.1.6+8-201902111843)
    Dynamic Code Evolution 64-Bit Server VM AdoptOpenJDK (build 11.0.1.6+8-201902111843, mixed mode)

Download: https://github.com/HotswapProjects/HotswapAgent/releases => https://github.com/HotswapProjects/HotswapAgent/releases/download/1.3.1-SNAPSHOT/hotswap-agent-1.3.1-SNAPSHOT.jar

Add this dependency temporarily, in order to get it into your maven repo:

    <dependency>
        <groupId>fish.payara.extras</groupId>
        <artifactId>payara-micro</artifactId>
        <version>5.191</version>
    </dependency>

Then you can run your app in one of several ways (note this does NOT use the DCE jdk, no idea how it works - it uses their agent though):

    java -javaagent:/home/ant/Downloads/hotswap-agent-1.3.1-SNAPSHOT.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar /home/ant/.m2/repository/fish/payara/extras/payara-micro/5.184/payara-micro-5.184.jar --deploy web/target/web

That runs the web app using payara micro jar, attaching the hotswap-agent and the debugger, using an exploded folder created by maven during package I think.
This way you can make changes to the html code, and hit refresh in the browser. But it doesn't quite work - you need to change the code in the maven folder,
not the browser. Or can we convince intellij to "compile" code from our web folder into that folder? Like in the old days where we told eclipse to compile into
`WEB-INF/classes`? I guess we could use a file watcher to copy changed content to that exploded folder?

Alternatively, run the uberjar, but then you can't just refresh the browser to get changes:

    java -javaagent:/home/ant/Downloads/hotswap-agent-1.3.1-SNAPSHOT.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar web/target/web-microbundle.jar

Using the DCEVM JVM caused problems with finding jax-b classes - don't bother:

    /usr/lib/jvm/dcevm-11.0.1+8/bin/java -javaagent:/home/ant/Downloads/hotswap-agent-1.3.1-SNAPSHOT.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -jar web/target/web-microbundle.jar

