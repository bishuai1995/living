package cn.aisino.living.service.webrtc.room;

import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;

public class UserSession implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final WebSocketSession session;
    private final WebRtcEndpoint webRtcEndpoint;

    public UserSession(WebSocketSession session, WebRtcEndpoint webRtcEndpoint) {
        this.session = session;
        this.webRtcEndpoint = webRtcEndpoint;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }

    public void addCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }

    public void sendMessage(JsonObject message) throws IOException {
        session.sendMessage(new TextMessage(message.toString()));
    }

    @Override
    public void close() throws IOException {

    }
}
