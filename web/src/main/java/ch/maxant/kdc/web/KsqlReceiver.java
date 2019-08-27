package ch.maxant.kdc.web;

import ch.maxant.kdc.library.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.websocket.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class KsqlReceiver {

    @Inject
    WebSocketModel clients;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Client client;

    public void onStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        System.out.println("\r\n\r\nSTARTED\r\n");

        // ////////////////////////////////////////////////////////////////////////////////////////
        // this works, but it is a good example of how not to do it. the select statement is only
        // running on one instance of the ksql server. it subscribes to all partitions.
        // a better way to do it is to subscribe to the sink topic which the T_YOUNG_PARTNERS ksql
        // is writing to!
        // ////////////////////////////////////////////////////////////////////////////////////////

        WebTarget target = client.target("http://maxant.ch:30401/");
        Invocation.Builder request = target.path("query").request();
        String body = "{" +
                "   \"ksql\": \"SELECT * FROM T_YOUNG_PARTNERS;\"," +
                "   \"streamsProperties\": {" +
                "       \"ksql.streams.auto.offset.reset\": \"latest\"" +
                "   }" +
                "}";
        try (Response response = request.post(Entity.entity(body, "application/vnd.ksql.v1+json"))) {
            if(response.getStatus() == Response.Status.OK.getStatusCode()) {
                InputStream is = (InputStream) response.getEntity();
                int current = 0;
                StringBuilder sb = new StringBuilder();
                while((current = is.read()) != -1) {
                    sb.append((char)current);
                    if(current == '\r' || current == '\n') {
                        String data = sb.toString().trim();
                        sb = new StringBuilder();
                        if(!data.isEmpty()) {
                            System.out.println("RESPONSE: " + data);

                            KsqlResponseEntity e = objectMapper.readValue(data.trim(), KsqlResponseEntity.class);

                            String pid = (String)e.getRow().getColumns().get(1);
                            String dob = (String)e.getRow().getColumns().get(5);

                            String json = "{\"pid\": \"" + pid + "\", \"dob\": \"" + dob + "\"}";
                            clients.sendToAll("new young partner : " + json);
                        }
                    }
                }
                System.out.println("done reading response");
            } else {
                System.err.println("got a response: " + response.getStatus());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        KsqlReceiver receiver = new KsqlReceiver();
        Client client = ClientBuilder.newClient();
        receiver.client = client;
        receiver.objectMapper = JacksonConfig.getMapper();
        receiver.clients = new WebSocketModel();
        receiver.clients.put("1", new MySession() {
        });
        receiver.onStartup(null);
        System.out.println("done");
    }

    private static class MySession implements Session {
        @Override
        public WebSocketContainer getContainer() {
            return null;
        }

        @Override
        public void addMessageHandler(MessageHandler handler) throws IllegalStateException {

        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {

        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {

        }

        @Override
        public Set<MessageHandler> getMessageHandlers() {
            return null;
        }

        @Override
        public void removeMessageHandler(MessageHandler handler) {

        }

        @Override
        public String getProtocolVersion() {
            return null;
        }

        @Override
        public String getNegotiatedSubprotocol() {
            return null;
        }

        @Override
        public List<Extension> getNegotiatedExtensions() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public long getMaxIdleTimeout() {
            return 0;
        }

        @Override
        public void setMaxIdleTimeout(long milliseconds) {

        }

        @Override
        public void setMaxBinaryMessageBufferSize(int length) {

        }

        @Override
        public int getMaxBinaryMessageBufferSize() {
            return 0;
        }

        @Override
        public void setMaxTextMessageBufferSize(int length) {

        }

        @Override
        public int getMaxTextMessageBufferSize() {
            return 0;
        }

        @Override
        public RemoteEndpoint.Async getAsyncRemote() {
            return null;
        }

        @Override
        public RemoteEndpoint.Basic getBasicRemote() {
            return new MyBasic();
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void close(CloseReason closeReason) throws IOException {

        }

        @Override
        public URI getRequestURI() {
            return null;
        }

        @Override
        public Map<String, List<String>> getRequestParameterMap() {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public Map<String, String> getPathParameters() {
            return null;
        }

        @Override
        public Map<String, Object> getUserProperties() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public Set<Session> getOpenSessions() {
            return null;
        }

        private class MyBasic implements RemoteEndpoint.Basic {
            @Override
            public void sendText(String text) throws IOException {
                System.out.println("WS Client received Text: " + text);
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {

            }

            @Override
            public void sendText(String partialMessage, boolean isLast) throws IOException {

            }

            @Override
            public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {

            }

            @Override
            public OutputStream getSendStream() throws IOException {
                return null;
            }

            @Override
            public Writer getSendWriter() throws IOException {
                return null;
            }

            @Override
            public void sendObject(Object data) throws IOException, EncodeException {

            }

            @Override
            public void setBatchingAllowed(boolean allowed) throws IOException {

            }

            @Override
            public boolean getBatchingAllowed() {
                return false;
            }

            @Override
            public void flushBatch() throws IOException {

            }

            @Override
            public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {

            }

            @Override
            public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {

            }
        }
    }
}
