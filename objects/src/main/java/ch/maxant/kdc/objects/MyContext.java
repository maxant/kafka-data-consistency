package ch.maxant.kdc.objects;

import javax.enterprise.context.RequestScoped;

@RequestScoped
public class MyContext {
    private String username;
    private String thread1;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getThread1() {
        return thread1;
    }

    public void setThread1(String thread1) {
        this.thread1 = thread1;
    }
}
