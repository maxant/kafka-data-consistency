package ch.maxant.kdc.web;

import javax.inject.Inject;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws")
public class WebSocketServer {

    @Inject
    WebSocketModel model;

    @OnOpen
    public void open(Session session) {
        System.out.println("open session " + session.getId());
        model.put(session.getId(), session);
    }

    @OnClose public void close(Session session) {
        System.out.println("close session " + session.getId());
        model.remove(session.getId());
    }

    @OnError public void onError(Throwable error) {
        System.err.println("error on ws");
        error.printStackTrace();
    }

    @OnMessage public void handleMessage(String message, Session session) {
        System.out.println("message on session " + session.getId() + ": " + message);
    }

}