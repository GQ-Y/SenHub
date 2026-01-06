package com.hikvision.nvr.hikvision;

import com.hikvision.nvr.Common.osSelect;
import com.hikvision.nvr.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK集成测试类
 * 用于测试SDK的基本功能是否正常
 */
public class SDKTest {
    private static final Logger logger = LoggerFactory.getLogger(SDKTest.class);

    public static void main(String[] args) {
        logger.info("开始SDK集成测试...");

        try {
            // 创建测试配置
            Config.SdkConfig sdkConfig = new Config.SdkConfig();
            sdkConfig.setLibPath(System.getProperty("user.dir") + "/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll");
            sdkConfig.setLogPath("./sdkLog");
            sdkConfig.setLogLevel(3);

            // 初始化SDK
            HikvisionSDK sdk = HikvisionSDK.getInstance();
            logger.info("尝试初始化SDK...");
            
            if (!sdk.init(sdkConfig)) {
                logger.error("SDK初始化失败，错误码: {}", sdk.getLastError());
                System.exit(1);
            }

            logger.info("✓ SDK初始化成功");

            // 测试获取SDK接口
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK != null) {
                logger.info("✓ SDK接口获取成功");
            } else {
                logger.error("✗ SDK接口获取失败");
                System.exit(1);
            }

            // 测试获取错误码（应该返回0或有效值）
            int errorCode = sdk.getLastError();
            logger.info("✓ 获取错误码成功: {}", errorCode);

            // 测试系统信息
            logger.info("操作系统: {}", System.getProperty("os.name"));
            logger.info("是否为Linux: {}", osSelect.isLinux());
            logger.info("是否为Windows: {}", osSelect.isWindows());

            logger.info("✓ SDK集成测试通过！");

            // 清理
            sdk.cleanup();
            logger.info("✓ SDK清理完成");

        } catch (Exception e) {
            logger.error("SDK集成测试失败", e);
            System.exit(1);
        }
    }
}
