package cn.aisino.living.service.webrtc.room;

import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class Room implements Closeable {

    private final Logger log = LoggerFactory.getLogger(Room.class);

    private final String roomId;
    private final ConcurrentHashMap<String, UserSession> viewers = new ConcurrentHashMap<>();
    private UserSession presenter;
    private MediaPipeline pipeline;

    public Room(MediaPipeline pipeline,String roomId) {
        this.pipeline = pipeline;
        this.roomId=roomId;
    }

    /**
     * 房间内文字聊天
     * @param username
     * @param message
     * @param session
     * @throws IOException
     */
    public void chat(String username, String message, WebSocketSession session) throws IOException{
        String sessionId = session.getId();
        //封装返回信息
        JsonObject response=new JsonObject();
        response.addProperty("id","chatResponse");
        if(presenter!=null&&presenter.getSession().getId().equals(sessionId)){ //主播发送的信息
            response.addProperty("response", "accepted");
            response.addProperty("sender",username);
            response.addProperty("content",message);
            response.addProperty("isPresenter",true);
            //发送给主播
            presenter.sendMessage(response);
            for(UserSession viewer:viewers.values()){
                viewer.sendMessage(response);
            }
        }else if(viewers.containsKey(sessionId)){ //观众发送的信息
            response.addProperty("response", "accepted");
            response.addProperty("sender",username);
            response.addProperty("content",message);
            response.addProperty("isPresenter",false);
            //发送给主播
            presenter.sendMessage(response);
            //发送给其他观众
            for(UserSession viewer:viewers.values()){
                viewer.sendMessage(response);
            }
        }else {
            response.addProperty("response", "rejected");
            session.sendMessage(new TextMessage(response.toString()));
        }
    }

    /**
     * 主播进入房间
     * @param sdpOffer
     * @param session
     */
    public void join(String sdpOffer,WebSocketSession session) throws IOException{
        if(presenter==null){
            presenter = new UserSession(session, new WebRtcEndpoint.Builder(this.pipeline).build());
            WebRtcEndpoint presenterWebRtc = presenter.getWebRtcEndpoint();
            //candidate采集
            iceCandidateFoundListener(presenterWebRtc,session);
            //开始采集sdpOffer
            String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);
            //封装返回信息
            JsonObject response = new JsonObject();
            response.addProperty("id", "presenterResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", sdpAnswer);
            synchronized (session) {
                presenter.sendMessage(response);
            }
            presenterWebRtc.gatherCandidates();
        }
    }

    /**
     * 观众加入房间观看
     * @param sdpOffer
     * @param session
     * @throws IOException
     */
    public void viewer(String sdpOffer,WebSocketSession session) throws IOException{
        if(presenter!=null){
            if(viewers.containsKey(session.getId())){
                JsonObject response = new JsonObject();
                response.addProperty("id", "viewerResponse");
                response.addProperty("response", "rejected");
                session.sendMessage(new TextMessage(response.toString()));
                return;
            }
            UserSession viewer = new UserSession(session, new WebRtcEndpoint.Builder(pipeline).build());
            WebRtcEndpoint viewerWebRtc = viewer.getWebRtcEndpoint();
            //candidate采集
            iceCandidateFoundListener(viewerWebRtc,session);
            //向该房间中添加观众，并与主播绑定
            viewers.put(session.getId(),viewer);
            presenter.getWebRtcEndpoint().connect(viewerWebRtc);
            //开始采集sdpOffer
            String sdpAnswer = viewerWebRtc.processOffer(sdpOffer);
            //封装返回信息
            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", sdpAnswer);
            synchronized (session) {
                viewer.sendMessage(response);
            }
            viewerWebRtc.gatherCandidates();
        }
    }

    /**
     * candidate采集
     * @param webRtcEndpoint
     * @param session
     */
    private void iceCandidateFoundListener(WebRtcEndpoint webRtcEndpoint,WebSocketSession session){
        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "iceCandidate");
                response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(response.toString()));
                    }
                } catch (IOException e) {
                    log.debug(e.getMessage());
                }
            }
        });
    }

    /**
     * 处理onIceCandidate事件
     * @param candidate
     * @param session
     */
    public void onIceCandidate(JsonObject candidate, WebSocketSession session){
        UserSession user=null;
        if(presenter!=null){
            if(presenter.getSession()==session){
                user=presenter;
            }else {
                user=viewers.get(session.getId());
            }
        }
        if(user!=null){
            IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(),
                            candidate.get("sdpMLineIndex").getAsInt());
            user.addCandidate(cand);
        }
    }

    /**
     * 离开房间
     * @param session
     */
    public void leave(WebSocketSession session) throws IOException {
        String sessionId = session.getId();
        if(presenter!=null&&presenter.getSession().getId().equals(sessionId)){ //主播退出房间
            for(UserSession viewer:viewers.values()){
                JsonObject response=new JsonObject();
                response.addProperty("id","stopCommunication");
                viewer.sendMessage(response);
            }
            if(pipeline!=null){
                pipeline.release();
            }
            pipeline=null;
            presenter=null;
        }else if(viewers.containsKey(sessionId)){ //观众退出房间
            if(viewers.get(sessionId).getWebRtcEndpoint()!=null){
                viewers.get(sessionId).getWebRtcEndpoint().release();
            }
            viewers.remove(sessionId);
        }
    }

    /**
     * 获取房间内的人数
     * @return
     */
    public Integer getUserCount(){
        Integer count = viewers.size();
        if(presenter!=null&&pipeline!=null){
            count++;
        }
        return count;
    }

    /**
     * 获取当前房间的房间号
     * @return
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * 获取当前房间的主播信息
     * @return
     */
    public UserSession getPresenter() {
        return presenter;
    }

    /**
     * 获取当前房间的所有观众信息
     * @return
     */
    public ConcurrentHashMap<String, UserSession> getViewers() {
        return viewers;
    }

    @Override
    public void close() throws IOException {

    }
}
