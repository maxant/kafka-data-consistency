package ch.maxant.kdc.web;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class WebSocketModel {

    Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void sendToAll(String msg) {
        sessions.values().forEach(s -> {
            try {
                s.getBasicRemote().sendText(msg);
            } catch(IOException e){
                System.err.println("failed to send message to session " + s.getId());
                e.printStackTrace();
                System.err.println("removing session " + s.getId());
                remove(s.getId());
            }
        });
    }

    public void put(String id, Session session) {
        sessions.put(id, session);
    }

    public void remove(String id) {
        sessions.remove(id);
    }
}