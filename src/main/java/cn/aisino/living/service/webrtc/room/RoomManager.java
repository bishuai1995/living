package cn.aisino.living.service.webrtc.room;

import com.google.gson.JsonObject;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Logger log = LoggerFactory.getLogger(RoomManager.class);

    @Autowired
    private KurentoClient kurento;

    private final ConcurrentHashMap<String,Room> rooms=new ConcurrentHashMap<>();

    /**
     * 房间内文字聊天
     * @param roomId
     * @param username
     * @param session
     * @throws IOException
     */
    public void chatInRoom(String roomId, String username, String message, WebSocketSession session) throws IOException{
        Room room=rooms.get(roomId);
        if(room!=null){
            room.chat(username,message,session);
        }
    }

    /**
     * 主播加入房间，如果房间不存在，则创建房间
     * @param roomId
     * @param sdpOffer
     * @return
     */
    public synchronized void joinRoom(String roomId,String sdpOffer, WebSocketSession session) throws IOException {
        Room room=rooms.get(roomId);
        if(room==null){
            room=new Room(kurento.createMediaPipeline(),roomId);
            room.join(sdpOffer,session);
            rooms.put(roomId,room);
        }else{
            JsonObject response = new JsonObject();
            response.addProperty("id", "presenterResponse");
            response.addProperty("response", "rejected");
            session.sendMessage(new TextMessage(response.toString()));
        }
    }

    /**
     * 观众进入房间观看，如果没有主播，则退出
     * @param roomId
     * @param sdpOffer
     * @param session
     * @throws IOException
     */
    public synchronized void viewerRoom(String roomId,String sdpOffer, WebSocketSession session) throws IOException {
        Room room=rooms.get(roomId);
        if(room==null){
            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "rejected");
            session.sendMessage(new TextMessage(response.toString()));
        }else{
            room.viewer(sdpOffer,session);
        }
    }

    /**
     * 处理onIceCandidate事件
     * @param roomId
     * @param candidate
     * @param session
     */
    public synchronized void handleOnIceCandidate(String roomId,JsonObject candidate, WebSocketSession session){
        Room room = rooms.get(roomId);
        if(room!=null){
            room.onIceCandidate(candidate,session);
        }
    }

    /**
     * 离开房间
     * @param roomId
     * @param session
     * @throws IOException
     */
    public synchronized void leaveRoom(String roomId, WebSocketSession session) throws IOException {
        Room room = rooms.get(roomId);
        if(room!=null){
            room.leave(session);
            //如果房间内没有人
            if(room.getUserCount()==0){
                rooms.remove(roomId);
            }
        }
    }

    /**
     * WebSocket连接强制关闭
     * @param session
     */
    public synchronized void stop(WebSocketSession session) throws IOException{
        Boolean presenterIsExist=false;
        Boolean viewerIsExist;
        if(rooms.size()>0){
            for(Room room:rooms.values()){
                //房间内是否存在该主播
                if(room.getPresenter()!=null){
                    presenterIsExist=room.getPresenter().getSession()==session;
                }
                //房间内是否存在该观众
                viewerIsExist= room.getViewers().containsKey(session.getId());
                if(presenterIsExist||viewerIsExist){
                    room.leave(session);
                    //如果房间内没有人
                    if(room.getUserCount()==0){
                        rooms.remove(room.getRoomId());
                    }
                    break;
                }
            }
        }
    }
}
