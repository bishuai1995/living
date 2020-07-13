# living
项目简介：基于webrtc修改kurento源码实现one2many实时直播。
运行项目之前请自行搭建coturn服务、kurento流媒体服务器，详细请见本人CSDN博客：
该项目是在kurento-one2many基础之上进行二次开发修改，对原有的kurento-utils.js源代码进行重构，封装后台接口进而实现多房间直播，项目包括实时直播、切换摄像头、共享屏幕、文字聊天。
为方便大家阅读、二次开发，所以前端界面设计简单哈哈。
运行前请修改application-dev.properties的kurento流媒体服务器地址，项目中的服务器地址为测试地址已经失效。
运行前请修改kurento.js中203行的turn穿透服务地址，项目中的turn地址可以正常使用。
最后欢迎大家对该项目添砖加瓦，让该项目更加丰富、稳定。
