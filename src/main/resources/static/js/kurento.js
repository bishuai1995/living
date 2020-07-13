let localVideo=document.querySelector('video#localvideo');
let remoteVideo=document.querySelector('video#remotevideo');
let btnStart=document.querySelector('button#start');
let btnLeave=document.querySelector('button#leave');
let localStream=null;
let remoteStream=null;
let roomId;
let username;
let pc=null;
let socket=null;
let deviceValue=null;
let isPresenter=false;

navigator.mediaDevices.enumerateDevices().then(gotDevices);

/**
 * 获取摄像头列表
 */
function gotDevices(devicesInfos){
    let videoSource=document.querySelector("select#videoSource");
    if(videoSource){
        videoSource.options.length = 0;
        let count = 1;
        devicesInfos.forEach(function(devicesInfo){
            if(devicesInfo.kind==='videoinput'){
                let option=document.createElement('option');
                option.text = devicesInfo.label || 'Camera'+(count++);
                option.value=devicesInfo.deviceId;
                videoSource.appendChild(option);
            }
        });
    }
}

/**
 * 切换摄像头
 */
$("#videoSource").change(function () {
    deviceValue=videoSource.value;
    if(localStream){
        closeLocalVideoMedia();
        let constraints=getConstraints();
        navigator.mediaDevices.getUserMedia(constraints).then(function(stream){
            localVideo.srcObject=stream;
            pc.getSenders()[1].replaceTrack(stream.getVideoTracks()[0]);
        }).catch(function(error){
            console.error('Failed to get Media Stream!',error);
        });
    }
});

/**
 * 切换至桌面
 */
function shareDesk(){
    if(localStream) {
        closeLocalVideoMedia();
        navigator.mediaDevices.getDisplayMedia({video: true}).then(function (stream) {
            localVideo.srcObject=stream;
            pc.getSenders()[1].replaceTrack(stream.getVideoTracks()[0]);
        }).catch(handleError);
    }
}

/**
 * 开启直播
 * @param boolPresenter 是否是主播
 */
function start(boolPresenter){
    roomId=document.getElementById("roomId").value.trim();
    username=document.getElementById("username").value.trim();
    isPresenter=boolPresenter;
    if(roomId&&username){
        if(isPresenter){ //主播
            presenter();
        }else{ //观众
            viewer();
        }
    }
}

/**
 * 开启摄像头
 */
function presenter(){
    if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){
        console.error('The getUserMedia is not supported!');
        return false;
    }else{
        let constraints=getConstraints();
        navigator.mediaDevices.getUserMedia(constraints)
            .then(getMediaStream)
            .catch(handleError);
    }
}

/**
 * 根据选择的媒体设备获取相应的constraints
 */
function getConstraints(){
    let constraints;
    if (deviceValue) {
        let videoConstraints = {};
        videoConstraints.deviceId = { exact: deviceValue };
        videoConstraints.width = 640;
        videoConstraints.framerate = 15;
        constraints = {
            video: videoConstraints,
            audio:{
                noiseSuppression:true,
                echoCancellation:true
            }
        };
    } else {
        constraints={
            video:true,
            audio:{
                noiseSuppression:true,
                echoCancellation:true
            }
        };
    }
    return constraints;
}

/**
 * 获取媒体流
 * @param stream
 */
function getMediaStream(stream){
    localStream=stream;
    if(localVideo){
        localVideo.srcObject=localStream;
    }
    conn();
}

/**
 * 处理异常
 * @param err
 */
function handleError(err){
    console.error('Failed to get Media Stream!',err);
}

/**
 * 开启摄像头
 */
function viewer(){
    conn();
}

/**
 * 建立信令连接
 * @returns {boolean}
 */
function conn(){
    socket = new WebSocket('wss://' + location.host + '/call');

    socket.onopen = function (event) {
        createPeerConnection();
    };

    socket.onmessage = function(message) {
        let parsedMessage = JSON.parse(message.data);

        switch (parsedMessage.id) {
            case 'chatResponse':
                chatResponse(parsedMessage);
                break;
            case 'presenterResponse':
                presenterResponse(parsedMessage);
                break;
            case 'viewerResponse':
                presenterResponse(parsedMessage);
                break;
            case 'iceCandidate':
                let candidate=new RTCIceCandidate({
                    sdpMid:parsedMessage.candidate.sdpMid,
                    sdpMLineIndex:parsedMessage.candidate.sdpMLineIndex,
                    candidate:parsedMessage.candidate.candidate
                });
                pc.addIceCandidate(candidate);
                break;
            case 'stopCommunication':
                leave();
                break;
            default:
                console.error('Unrecognized message', parsedMessage);
        }
    };
    return false;
}

/**
 * 创建PeerConnection对象
 */
function createPeerConnection(){
    if(!pc){
        let pcConfig={
            'iceServers':[{
                "urls" : "turn:47.93.25.18:3478",
                "username" : "helloworld",
                "credential" : "helloworld"
            }]
        };
        pc=new RTCPeerConnection(pcConfig);

        pc.onicecandidate=(e)=>{
            if(e.candidate){
                let message = {
                    "id" : "onIceCandidate",
                    "roomId" : roomId,
                    "candidate" : e.candidate
                };
                sendMessage(message);
            }
        };
    }

    if(localStream){
        localStream.getTracks().forEach((track)=>{
            pc.addTrack(track,localStream);
        });
    }

    if(!isPresenter){
        pc.ontrack=(e)=>{
            remoteVideo.srcObject=e.streams[0];
            remoteStream=e.streams[0];
        };
    }
    call();
}

/**
 * 建立对等连接
 */
function call(){
    if(pc){
        let options={
            offerToReceiveAudio:1,
            offerToReceiveVideo:1
        };
        pc.createOffer(options)
            .then(getOffer)
            .catch(handleOfferError);
    }
}

/**
 * 获取Offer
 * @param desc
 */
function getOffer(desc){
    pc.setLocalDescription(desc);
    let message;
    if(isPresenter){
        message = {
            "id" : "joinRoom",
            "roomId" : roomId,
            "sdpOffer" : desc.sdp
        };
    }else{
        message = {
            "id" : "viewerRoom",
            "roomId" : roomId,
            "sdpOffer" : desc.sdp
        };
    }
    sendMessage(message);
}

/**
 * 处理Offer Sdp异常
 * @param err
 */
function handleOfferError(err){
    console.log('Failed to get Offer!',err);
}

/**
 * 发送聊天信息
 */
function sendMsg() {
    let content=document.getElementById("sendMsg").value.trim();
    if (content&&socket) {
        let message = {
            "id" : "chat",
            "username" : username,
            "roomId" : roomId,
            "content" : content
        };
        sendMessage(message);
    }
    document.getElementById("sendMsg").value = "";
}

/**
 * 文本聊天响应
 * @param message
 */
function chatResponse(message){
    if(message.response == 'accepted'){
        if(message.isPresenter){ //主播发送信息
            document.getElementById("content").append("(主播)"+message.sender+":"+message.content + "\r\n");
        }else{ //观众发送信息
            document.getElementById("content").append(message.sender+":"+message.content + "\r\n");
        }
    }
}

/**
 * 主播/观众响应
 * @param message
 */
function presenterResponse(message) {
    if (message.response != 'accepted') {
        let errorMsg = message.message ? message.message : 'Unknow error';
        console.info('Call not accepted for the following reason: ' + errorMsg);
        dispose();
    } else {
        pc.setRemoteDescription(new RTCSessionDescription({
            type: 'answer',
            sdp: message.sdpAnswer
        }));
        btnStart.disabled=true;
        btnLeave.disabled=false;
    }
}

/**
 * 离开房间
 */
function leave() {
    let message = {
        "id" : "leaveRoom",
        "roomId" : roomId
    };
    sendMessage(message);
    dispose();
}

/**
 * 资源释放
 */
function dispose() {
    if(socket){
        socket.close();
    }
    closePeerConnection();
    closeMedia();
    btnStart.disabled=false;
    btnLeave.disabled=true;
    deviceValue=null;
}

/**
 * 关闭RTCPeerConnection对象
 */
function closePeerConnection(){
    if(pc){
        pc.close();
        pc=null;
    }
}

/**
 * 关闭媒体轨道
 */
function closeMedia(){
    closeLocalMedia();
    closeRemoteMedia();
}

/**
 * 关闭本地媒体轨
 */
function closeLocalMedia(){
    //本地媒体流关闭
    if(localStream&&localStream.getTracks()){
        localStream.getTracks().forEach((track)=>{
            track.stop();
        });
    }
    localStream=null;
}

/**
 * 关闭本地视频轨
 */
function closeLocalVideoMedia(){
    if(localStream&&localStream.getTracks()){
        localStream.getVideoTracks().forEach(function (track) {
            track.stop();
        });
    }
}

/**
 * 关闭远端媒体轨
 */
function closeRemoteMedia(){
    //远端媒体流关闭
    if(remoteStream&&remoteStream.getTracks()){
        remoteStream.getTracks().forEach((track)=>{
            track.stop();
        });
    }
    remoteStream=null;
}

/**
 * 发送信令
 * @param message
 */
function sendMessage(message) {
    let jsonMessage = JSON.stringify(message);
    socket.send(jsonMessage);
}

/**
 * 监听页面刷新
 */
window.onbeforeunload = function() {
    if(socket){
        socket.close();
    }
};