package com.digital.video.gateway.tiandy;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.CallbackReference;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 天地伟业抓图 Demo：两台设备，等预览流首帧后再按帧抓图，每台抓 100 张后停止。
 * 设备1: 192.168.1.10  设备2: 192.168.1.200  账号密码: admin / zyckj2021
 */
public class TiandyCaptureDemo {

    private static void log(String msg) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        System.out.println("[" + ts + "] " + msg);
    }

    private static NvssdkLibrary lib;
    /** 预览流首帧已收到时置为 true，用于判断“预览流成功”后再抓图 */
    private static volatile boolean firstFrameReceived;

    private static final String[][] DEVICES = {
            { "192.168.1.10", "3000", "admin", "zyckj2021" },
            { "192.168.1.200", "3000", "admin", "zyckj2021" }
    };
    private static final int CAPTURE_COUNT = 100;
    private static final int FRAME_INTERVAL_MS = 80;
    private static final int FIRST_FRAME_WAIT_TIMEOUT_MS = 15_000;

    public static void main(String[] args) throws Exception {
        log("========== 天地伟业抓图 Demo 启动 ==========");
        log("设备数=" + DEVICES.length + ", 每台抓图 " + CAPTURE_COUNT + " 次后停止，等首帧后再抓");

        // 1. 加载库
        String libDir = System.getProperty("tiandy.lib.path", System.getProperty("user.dir") + "/lib/x86/tiandy");
        File so = new File(libDir, "libnvssdk.so");
        if (!so.exists()) {
            so = new File(libDir, "lib/linux/libnvssdk.so");
            if (!so.exists()) {
                so = new File(System.getProperty("user.dir"), "lib/linux/libnvssdk.so");
            }
        }
        String libPath = so.getAbsolutePath();
        log("[STEP 1] 加载库: " + libPath);
        if (!so.exists()) {
            log("[STEP 1] 失败: 文件不存在");
            System.exit(1);
        }
        System.setProperty("java.library.path", so.getParent() + File.pathSeparator + System.getProperty("java.library.path", ""));
        try {
            lib = Native.load(libPath, NvssdkLibrary.class);
        } catch (UnsatisfiedLinkError e) {
            log("[STEP 1] 加载失败: " + e.getMessage());
            System.exit(1);
        }
        log("[STEP 1] 库加载成功");

        // 2. 回调（首帧回调用于判断预览流成功）
        NvssdkLibrary.FULLFRAME_NOTIFY_V4 cbkFullFrame = (iConnectID, iStreamType, pcData, iLen, pvHeader, pvUserData) -> {
            if (!firstFrameReceived) {
                firstFrameReceived = true;
            }
        };
        NvssdkLibrary.RAWFRAME_NOTIFY cbkRawFrame = (uiID, pcData, iLen, ptRawFrameInfo, lpUserData) -> { };
        int setNotify = lib.NetClient_SetNotifyFunction_V4(
                (iLogonID, wParam, lParam, notifyUserData) -> { },
                (ulLogonID, iChan, iAlarmState, iAlarmType, iUser) -> { },
                (ulLogonID, iChan, iParaType, strPara, iUser) -> { },
                (ulLogonID, cData, iLen, iComNo, iUser) -> { },
                (ulLogonID, iCmdKey, cData, iLen, iUser) -> { });
        log("[STEP 2] SetNotifyFunction_V4 返回: " + setNotify);
        if (setNotify != NvssdkLibrary.RET_SUCCESS) {
            System.exit(1);
        }

        // 3. Startup
        int startup = lib.NetClient_Startup_V4(0, 0, 0);
        log("[STEP 3] Startup 返回: " + startup);
        if (startup != NvssdkLibrary.RET_SUCCESS) {
            System.exit(1);
        }

        String outDir = System.getProperty("user.dir");
        int channelNo = 0;
        int streamNo = 1;

        for (int d = 0; d < DEVICES.length; d++) {
            String ip = DEVICES[d][0];
            int port = Integer.parseInt(DEVICES[d][1]);
            String user = DEVICES[d][2];
            String pass = DEVICES[d][3];
            log("========== 设备 " + (d + 1) + "/" + DEVICES.length + ": " + ip + ":" + port + " ==========");

            // 4. SyncLogon
            TiandySDKStructure.tagLogonPara tLogon = new TiandySDKStructure.tagLogonPara();
            tLogon.iSize = tLogon.size();
            tLogon.cNvsIP = ip.getBytes("UTF-8");
            tLogon.iNvsPort = port;
            tLogon.cUserName = user.getBytes("UTF-8");
            tLogon.cUserPwd = pass.getBytes("UTF-8");
            tLogon.cCharSet = "UTF-8".getBytes("UTF-8");
            tLogon.write();
            int logonID = lib.NetClient_SyncLogon(NvssdkLibrary.SERVER_NORMAL, tLogon.getPointer(), tLogon.iSize);
            log("[STEP 4] SyncLogon 返回 logonID: " + logonID);
            if (logonID < 0) {
                log("  登录失败，跳过该设备");
                continue;
            }

            IntByReference piDigital = new IntByReference();
            lib.NetClient_GetDigitalChannelNum(logonID, piDigital);
            int digitalCount = piDigital.getValue();
            IntByReference piTotal = new IntByReference();
            lib.NetClient_GetChannelNum(logonID, piTotal);
            int totalCount = piTotal.getValue();
            if (digitalCount == 0) digitalCount = totalCount;
            if (channelNo < 0 || channelNo >= digitalCount) {
                log("  通道号无效，跳过");
                lib.NetClient_Logoff(logonID);
                continue;
            }

            // 5. SyncRealPlay
            TiandySDKStructure.tagNetClientPara tVideoPara = new TiandySDKStructure.tagNetClientPara();
            tVideoPara.iSize = tVideoPara.size();
            tVideoPara.tCltInfo.m_iServerID = logonID;
            tVideoPara.tCltInfo.m_iChannelNo = channelNo;
            tVideoPara.tCltInfo.m_iStreamNO = streamNo;
            tVideoPara.tCltInfo.m_iNetMode = 1;
            tVideoPara.tCltInfo.m_iTimeout = 20;
            tVideoPara.pCbkFullFrm = CallbackReference.getFunctionPointer(cbkFullFrame);
            tVideoPara.pvCbkFullFrmUsrData = null;
            tVideoPara.pCbkRawFrm = CallbackReference.getFunctionPointer(cbkRawFrame);
            tVideoPara.pvCbkRawFrmUsrData = null;
            tVideoPara.iIsForbidDecode = NvssdkLibrary.RAW_NOTIFY_ALLOW_DECODE;
            tVideoPara.pvWnd = null;
            tVideoPara.tCltInfo.write();
            tVideoPara.write();

            firstFrameReceived = false;
            IntByReference piConnectID = new IntByReference();
            int syncRet = lib.NetClient_SyncRealPlay(piConnectID, tVideoPara.getPointer(), tVideoPara.iSize);
            log("[STEP 6] NetClient_SyncRealPlay 返回: " + syncRet + ", connectID=" + (syncRet == 0 ? piConnectID.getValue() : "-"));
            if (syncRet != NvssdkLibrary.RET_SUCCESS) {
                log("  预览启动失败，跳过该设备");
                lib.NetClient_Logoff(logonID);
                continue;
            }
            int connectID = piConnectID.getValue();

            // 6. 判断预览流成功：等首帧回调，超时则回退为固定延迟 3 秒后再抓图
            log("[STEP 7] 等待预览流首帧（最多 " + (FIRST_FRAME_WAIT_TIMEOUT_MS / 1000) + " 秒）...");
            long deadline = System.currentTimeMillis() + FIRST_FRAME_WAIT_TIMEOUT_MS;
            while (!firstFrameReceived && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            if (!firstFrameReceived) {
                log("[STEP 7] 未收到首帧回调，回退为固定延迟 3 秒后抓图");
                Thread.sleep(3000);
            } else {
                log("[STEP 7] 已收到首帧");
            }
            log("[STEP 7] 开始按帧抓图 " + CAPTURE_COUNT + " 次");

            // 7. 每帧抓图 100 次后停止
            String prefix = "tiandy_" + ip.replace(".", "_") + "_";
            int ok = 0, fail = 0;
            for (int i = 0; i < CAPTURE_COUNT; i++) {
                String fileName = prefix + String.format("%04d", i + 1) + ".jpg";
                String filePath = new File(outDir, fileName).getAbsolutePath();
                ByteBuffer buf = ByteBuffer.wrap(filePath.getBytes());
                int capRet = lib.NetClient_CapturePicture(connectID, NvssdkLibrary.CAPTURE_PICTURE_TYPE_JPG, buf);
                if (capRet > 0) {
                    ok++;
                    if ((i + 1) % 20 == 0 || i == 0) {
                        log("  [CAPTURE] " + (i + 1) + "/" + CAPTURE_COUNT + " 成功，字节数=" + capRet);
                    }
                } else {
                    fail++;
                }
                if (i < CAPTURE_COUNT - 1) {
                    Thread.sleep(FRAME_INTERVAL_MS);
                }
            }
            log("  抓图完成: 成功=" + ok + ", 失败=" + fail + ", 输出目录=" + outDir);

            lib.NetClient_StopRealPlay(connectID, 1);
            lib.NetClient_Logoff(logonID);
            log("  已停止预览并登出设备 " + ip);
        }

        log("========== Demo 结束 ==========");
    }
}
