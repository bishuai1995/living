package cn.aisino.living;

import cn.aisino.living.service.webrtc.CallHandler;
import cn.aisino.living.service.webrtc.room.RoomManager;
import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@SpringBootApplication
@EnableWebSocket
public class LivingConfigure implements WebSocketConfigurer {

    @Value("${kms.url}")
    private String kmsUrl;

    @Bean
    public RoomManager roomManager() {
        return new RoomManager();
    }

    @Bean
    public CallHandler callHandler() {
        return new CallHandler();
    }

    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create();
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        System.setProperty("kms.url", kmsUrl);
        registry.addHandler(callHandler(), "/call").setAllowedOrigins("http://", "https://", "*");;
    }

}
