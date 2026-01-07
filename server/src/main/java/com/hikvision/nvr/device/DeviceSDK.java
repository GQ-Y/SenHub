package com.hikvision.nvr.device;

import com.hikvision.nvr.config.Config;

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
}
