package ch.maxant.kdc.objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class MyService2 {

    @Inject
    MyContext myContext;

    public void fillMyContext(String name) {
        System.out.println("Filling context in bean " + myContext.toString() + " on thread " + Thread.currentThread().getName() + " with username " + name);
        myContext.setThread1(Thread.currentThread().getName());
        myContext.setUsername(name);
    }

    public String getUsername() {
        System.out.println("Reading username " + myContext.getUsername() + " from " + myContext.toString() + " on thread " + Thread.currentThread().getName() + " after being filled by thread " + myContext.getThread1());
        return myContext.getUsername();
    }

    public String getThread1() {
        System.out.println("Reading Thread1 with username" + myContext.getUsername() + " from " + myContext.toString() + " on thread " + Thread.currentThread().getName() + " after being filled by thread " + myContext.getThread1());
        return myContext.getThread1();
    }
}
