package cn.aisino.living.service.webrtc;

import cn.aisino.living.service.webrtc.room.RoomManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

public class CallHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    @Autowired
    private RoomManager roomManager;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

        switch (jsonMessage.get("id").getAsString()) {
            case "chat":
                try{
                    chatInRoom(jsonMessage, session);
                }catch (Throwable t){
                    handleErrorResponse(jsonMessage, session, "presenterResponse");
                }
                break;
            case "joinRoom":
                try {
                    joinRoom(jsonMessage, session);
                }catch (Throwable t){
                    handleErrorResponse(jsonMessage, session, "presenterResponse");
                }
                break;
            case "viewerRoom":
                try {
                    viewerRoom(jsonMessage, session);
                }catch (Throwable t){
                    handleErrorResponse(jsonMessage, session, "viewerResponse");
                }
                break;
            case "onIceCandidate": {
                handleOnIceCandidate(jsonMessage, session);
                break;
            }
            case "leaveRoom":
                leaveRoom(jsonMessage,session);
                break;
            default:
                break;
        }
    }

    /**
     * 房间内文字聊天
     * @param params
     * @param session
     */
    private void chatInRoom(JsonObject params, WebSocketSession session) throws IOException{
        //获取房间号
        String roomId = params.get("roomId").getAsString();
        //获取用户名
        String username = params.get("username").getAsString();
        //获取发送信息
        String message=params.get("content").getAsString();
        roomManager.chatInRoom(roomId,username,message,session);
    }

    /**
     * 主播加入房间
     * @param params
     * @param session
     */
    private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
        //获取房间号
        String roomId = params.get("roomId").getAsString();
        //获取sdpOffer
        String sdpOffer = params.getAsJsonPrimitive("sdpOffer").getAsString();
        roomManager.joinRoom(roomId,sdpOffer,session);
    }

    /**
     * 观众进入房间观看
     * @param params
     * @param session
     */
    private void viewerRoom(JsonObject params, WebSocketSession session) throws IOException {
        //获取房间号
        String roomId = params.get("roomId").getAsString();
        //获取sdpOffer
        String sdpOffer = params.getAsJsonPrimitive("sdpOffer").getAsString();
        roomManager.viewerRoom(roomId,sdpOffer,session);
    }

    /**
     * 处理onIceCandidateo事件
     * @param params
     * @param session
     */
    private void handleOnIceCandidate(JsonObject params, WebSocketSession session){
        //获取房间号
        String roomId = params.get("roomId").getAsString();
        //获取candidate
        JsonObject candidate = params.get("candidate").getAsJsonObject();
        roomManager.handleOnIceCandidate(roomId,candidate,session);
    }

    /**
     * 离开房间
     * @param params
     * @param session
     * @throws IOException
     */
    private void leaveRoom(JsonObject params, WebSocketSession session) throws IOException{
        //获取房间号
        String roomId = params.get("roomId").getAsString();
        roomManager.leaveRoom(roomId,session);
    }

    /**
     * 异常处理
     * @param params
     * @param session
     * @param responseId
     * @throws IOException
     */
    private void handleErrorResponse(JsonObject params, WebSocketSession session, String responseId) throws IOException {
        leaveRoom(params,session);
        JsonObject response = new JsonObject();
        response.addProperty("id", responseId);
        response.addProperty("response", "rejected");
        session.sendMessage(new TextMessage(response.toString()));
    }

    /**
     * WebSocket连接强制停止
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        roomManager.stop(session);
    }
}
