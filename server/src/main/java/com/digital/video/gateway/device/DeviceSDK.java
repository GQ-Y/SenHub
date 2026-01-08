package com.digital.video.gateway.device;

import com.digital.video.gateway.config.Config;

import java.util.Date;
import java.util.List;

/**
 * 统一SDK接口
 * 所有品牌SDK都需要实现此接口
 */
public interface DeviceSDK {
    /**
     * 初始化SDK
     * @param config SDK配置
     * @return 是否初始化成功
     */
    boolean init(Config.SdkConfig config);
    
    /**
     * 登录设备
     * @param ip 设备IP地址
     * @param port 设备端口
     * @param username 用户名
     * @param password 密码
     * @return 登录句柄（userId），失败返回-1
     */
    int login(String ip, short port, String username, String password);
    
    /**
     * 登出设备
     * @param userId 登录句柄
     * @return 是否登出成功
     */
    boolean logout(int userId);
    
    /**
     * 获取最后错误码
     * @return 错误码
     */
    int getLastError();
    
    /**
     * 获取最后错误信息（字符串描述）
     * @return 错误信息
     */
    String getLastErrorString();
    
    /**
     * 清理SDK资源
     */
    void cleanup();
    
    /**
     * 获取品牌名称
     * @return 品牌名称（如：hikvision, tiandy, dahua）
     */
    String getBrand();
    
    // ========== 功能方法（可选实现，保持向后兼容） ==========
    
    /**
     * 启动预览
     * @param userId 登录句柄
     * @param channel 通道号
     * @param streamType 码流类型（0-主码流，1-子码流）
     * @return 预览连接ID（connectID），失败返回-1
     */
    default int startRealPlay(int userId, int channel, int streamType) {
        return -1; // 默认不支持，子类可重写
    }
    
    /**
     * 停止预览
     * @param connectId 预览连接ID
     * @return 是否成功
     */
    default boolean stopRealPlay(int connectId) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 开始录制
     * @param connectId 预览连接ID
     * @param filePath 录制文件路径
     * @return 是否成功
     */
    default boolean startRecording(int connectId, String filePath) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 停止录制
     * @param connectId 预览连接ID
     * @return 是否成功
     */
    default boolean stopRecording(int connectId) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 抓图
     * @param connectId 预览连接ID（某些SDK需要，如天地伟业）
     * @param userId 登录句柄（某些SDK需要，如海康）
     * @param channel 通道号
     * @param filePath 图片保存路径
     * @param pictureType 图片类型（0-YUV, 1-BMP, 2-JPG）
     * @return 是否成功
     */
    default boolean capturePicture(int connectId, int userId, int channel, String filePath, int pictureType) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 云台控制
     * @param userId 登录句柄
     * @param channel 通道号
     * @param command 控制命令（up/down/left/right/zoom_in/zoom_out）
     * @param action 动作（start/stop）
     * @param speed 速度（1-7）
     * @return 是否成功
     */
    default boolean ptzControl(int userId, int channel, String command, String action, int speed) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 查询回放文件
     * @param userId 登录句柄
     * @param channel 通道号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 回放文件列表
     */
    default List<PlaybackFile> queryPlaybackFiles(int userId, int channel, Date startTime, Date endTime) {
        return List.of(); // 默认返回空列表，子类可重写
    }
    
    /**
     * 按时间范围下载录像文件（从设备存储下载）
     * @param userId 登录句柄
     * @param channel 通道号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param localFilePath 本地保存文件路径
     * @param streamType 码流类型（0-主码流，1-子码流）
     * @return 下载连接ID（用于后续控制下载），失败返回-1
     */
    default int downloadPlaybackByTimeRange(int userId, int channel, Date startTime, Date endTime, 
                                             String localFilePath, int streamType) {
        return -1; // 默认不支持，子类可重写
    }
    
    /**
     * 停止下载录像
     * @param downloadId 下载连接ID
     * @return 是否成功
     */
    default boolean stopDownload(int downloadId) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 获取下载进度
     * @param downloadId 下载连接ID
     * @return 下载进度（0-100），失败返回-1
     */
    default int getDownloadProgress(int downloadId) {
        return -1; // 默认不支持，子类可重写
    }
    
    /**
     * 重启设备
     * @param userId 登录句柄
     * @return 是否成功
     */
    default boolean rebootDevice(int userId) {
        return false; // 默认不支持，子类可重写
    }
    
    /**
     * 回放文件信息
     */
    class PlaybackFile {
        private String fileName;
        private Date startTime;
        private Date endTime;
        private long fileSize;
        private int channel;
        
        public PlaybackFile(String fileName, Date startTime, Date endTime, long fileSize, int channel) {
            this.fileName = fileName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.fileSize = fileSize;
            this.channel = channel;
        }
        
        public String getFileName() { return fileName; }
        public Date getStartTime() { return startTime; }
        public Date getEndTime() { return endTime; }
        public long getFileSize() { return fileSize; }
        public int getChannel() { return channel; }
    }
}
