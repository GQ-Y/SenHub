package com.digital.video.gateway.hikvision;

import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备存储检查器
 * 用于检查设备是否有存储介质
 */
public class DeviceStorageChecker {
    private static final Logger logger = LoggerFactory.getLogger(DeviceStorageChecker.class);

    /**
     * 检查设备是否有存储介质
     * @param hcNetSDK SDK实例
     * @param userId 用户ID
     * @return true-有存储，false-无存储
     */
    public static boolean hasStorage(HCNetSDK hcNetSDK, int userId) {
        if (hcNetSDK == null || userId < 0) {
            return false;
        }

        try {
            // 使用NET_DVR_GET_HDCFG获取硬盘配置
            HCNetSDK.NET_DVR_HDCFG hdCfg = new HCNetSDK.NET_DVR_HDCFG();
            hdCfg.dwSize = hdCfg.size();
            hdCfg.write();

            IntByReference bytesReturned = new IntByReference(0);
            boolean result = hcNetSDK.NET_DVR_GetDVRConfig(
                userId,
                HCNetSDK.NET_DVR_GET_HDCFG,
                0, // channel
                hdCfg.getPointer(),
                hdCfg.size(),
                bytesReturned
            );

            if (!result) {
                int errorCode = hcNetSDK.NET_DVR_GetLastError();
                logger.warn("获取设备存储配置失败，错误码: {}", errorCode);
                // 如果获取失败，假设有存储（避免误判）
                return true;
            }

            hdCfg.read();

            // 检查是否有硬盘
            if (hdCfg.dwHDCount <= 0) {
                logger.warn("设备没有检测到存储介质（硬盘数: {}）", hdCfg.dwHDCount);
                return false;
            }

            // 检查是否有正常状态的硬盘
            boolean hasValidDisk = false;
            for (int i = 0; i < hdCfg.dwHDCount && i < HCNetSDK.MAX_DISKNUM_V30; i++) {
                HCNetSDK.NET_DVR_SINGLE_HD hd = hdCfg.struHDInfo[i];
                // dwHdStatus: 0-正常, 1-未格式化, 2-错误, 3-SMART状态, 4-不匹配, 5-休眠
                if (hd.dwHdStatus == 0) { // 正常状态
                    hasValidDisk = true;
                    break;
                }
            }

            if (!hasValidDisk) {
                logger.warn("设备没有正常状态的存储介质");
                return false;
            }

            logger.debug("设备存储检查通过，硬盘数: {}", hdCfg.dwHDCount);
            return true;

        } catch (Exception e) {
            logger.error("检查设备存储异常", e);
            // 异常时假设有存储（避免误判）
            return true;
        }
    }
}
