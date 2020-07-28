# 实时直播kurento-living项目
### 一、项目简介：

​	该项目是在kurento-one2many的基础之上修改源码来实现直播。通过对原有的kurento-utils.js源代码进行重构以及封装后台接口进而实现多房间直播，项目包括直播、切换摄像头、共享屏幕、文字聊天。

### 二、运行前需要安装、修改内容：

```javascript
1. 请自行搭建coturn穿透服务、kurento流媒体服务器，详细请见本人CSDN博客： https://mp.csdn.net/console/article 
2. 修改application-dev.properties的kurento流媒体服务器地址，项目中的服务器地址为测试地址已经失效。
3. 修改kurento.js中203行的coturn穿透服务器地址，项目中的turn地址可以正常使用。
```

​	项目运行后分为boroadcast.html（主播端页面）、audience.html页面（观众端页面），其中主播端页面需要先点击开启直播，观众端再点击观看直播才可以观看（可以修改后台java代码逻辑来满足个人需求）

​	为方便大家阅读、二次开发，所以前端界面设计粗陋。最后欢迎大家对该项目添砖加瓦，让该项目更加丰富、稳定。