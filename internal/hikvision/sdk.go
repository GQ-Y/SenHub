package hikvision

/*
#cgo CFLAGS: -I${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/incCn
#cgo LDFLAGS: -L${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll -lhcnetsdk -lHCCore -lPlayCtrl -lSuperRender -lAudioRender -lhpr -Wl,-rpath,${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll
#cgo LDFLAGS: -L${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom -lHCAlarm -lHCPreview -lHCPlayBack -lHCVoiceTalk -lHCCoreDevCfg -lHCGeneralCfgMgr -lHCIndustry -lHCDisplay -lanalyzedata -lAudioIntercom -lStreamTransClient -lSystemTransform -liconv -Wl,-rpath,${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom
#cgo LDFLAGS: -L${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll -lcrypto -lssl -lz -Wl,-rpath,${SRCDIR}/../../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll

#include "HCNetSDK.h"
#include <stdlib.h>
#include <string.h>

// 消息回调函数类型
typedef void (*MessageCallbackFunc)(LONG lCommand, NET_DVR_ALARMER *pAlarmer, char *pAlarmInfo, DWORD dwBufLen, void* pUser);

// C回调包装器
static void messageCallbackWrapper(LONG lCommand, NET_DVR_ALARMER *pAlarmer, char *pAlarmInfo, DWORD dwBufLen, void* pUser) {
    // 这里需要通过Go的CGO回调机制来处理
    // 暂时留空，后续通过Go层实现
}

// 异常回调函数类型
typedef void (*ExceptionCallbackFunc)(DWORD dwType, LONG lUserID, LONG lHandle, void *pUser);

// 实时数据回调函数类型
typedef void (*RealDataCallbackFunc)(LONG lRealHandle, DWORD dwDataType, BYTE *pBuffer, DWORD dwBufSize, void* dwUser);

// 回放数据回调函数类型
typedef void (*PlayDataCallbackFunc)(LONG lPlayHandle, DWORD dwDataType, BYTE *pBuffer, DWORD dwBufSize, DWORD dwUser);
*/
import "C"
import (
	"errors"
	"fmt"
	"unsafe"
)

// SDK错误码
const (
	NET_DVR_NOERROR = 0
	NET_DVR_ISFINDING = 1
	NET_DVR_FILE_SUCCESS = 2
	NET_DVR_FILE_NOFIND = 3
	NET_DVR_NOMOREFILE = 4
)

// 设备状态
const (
	DeviceStatusOnline  = "online"
	DeviceStatusOffline = "offline"
)

// SDK SDK封装结构
type SDK struct {
	initialized bool
}

// NewSDK 创建SDK实例
func NewSDK() *SDK {
	return &SDK{}
}

// Init 初始化SDK
func (s *SDK) Init() error {
	if s.initialized {
		return nil
	}
	
	result := C.NET_DVR_Init()
	if result == 0 {
		return errors.New("SDK初始化失败")
	}
	
	s.initialized = true
	return nil
}

// Cleanup 清理SDK
func (s *SDK) Cleanup() {
	if s.initialized {
		C.NET_DVR_Cleanup()
		s.initialized = false
	}
}

// SetLogToFile 设置日志文件
func (s *SDK) SetLogToFile(level int, logPath string) bool {
	cLogPath := C.CString(logPath)
	defer C.free(unsafe.Pointer(cLogPath))
	
	return C.NET_DVR_SetLogToFile(C.int(level), cLogPath) != 0
}

// GetLastError 获取最后错误码
func (s *SDK) GetLastError() int {
	return int(C.NET_DVR_GetLastError())
}

// LoginInfo 登录信息
type LoginInfo struct {
	IP       string
	Port     int
	Username string
	Password string
}

// DeviceInfo 设备信息
type DeviceInfo struct {
	SerialNumber string
	DeviceType   int
	Channels     int
	StartChannel int
	IP           string
	Port         int
	Name         string
}

// Login 登录设备
func (s *SDK) Login(info *LoginInfo) (int, *DeviceInfo, error) {
	if !s.initialized {
		return -1, nil, errors.New("SDK未初始化")
	}
	
	var loginInfo C.NET_DVR_USER_LOGIN_INFO
	var deviceInfo C.NET_DVR_DEVICEINFO_V40
	
	C.memset(unsafe.Pointer(&loginInfo), 0, C.sizeof_NET_DVR_USER_LOGIN_INFO)
	C.memset(unsafe.Pointer(&deviceInfo), 0, C.sizeof_NET_DVR_DEVICEINFO_V40)
	
	loginInfo.bUseAsynLogin = 0
	loginInfo.wPort = C.WORD(info.Port)
	
	cIP := C.CString(info.IP)
	defer C.free(unsafe.Pointer(cIP))
	C.strncpy((*C.char)(unsafe.Pointer(&loginInfo.sDeviceAddress[0])), cIP, C.NET_DVR_DEV_ADDRESS_MAX_LEN-1)
	
	cUsername := C.CString(info.Username)
	defer C.free(unsafe.Pointer(cUsername))
	C.strncpy((*C.char)(unsafe.Pointer(&loginInfo.sUserName[0])), cUsername, C.NAME_LEN-1)
	
	cPassword := C.CString(info.Password)
	defer C.free(unsafe.Pointer(cPassword))
	C.strncpy((*C.char)(unsafe.Pointer(&loginInfo.sPassword[0])), cPassword, C.NAME_LEN-1)
	
	userID := C.NET_DVR_Login_V40(&loginInfo, &deviceInfo)
	if userID < 0 {
		return -1, nil, fmt.Errorf("登录失败，错误码: %d", s.GetLastError())
	}
	
	devInfo := &DeviceInfo{
		SerialNumber: C.GoString((*C.char)(unsafe.Pointer(&deviceInfo.struDeviceV30.sSerialNumber[0]))),
		DeviceType:   int(deviceInfo.struDeviceV30.wDevType),
		Channels:     int(deviceInfo.struDeviceV30.byChanNum),
		StartChannel: int(deviceInfo.struDeviceV30.byStartChan),
		IP:           info.IP,
		Port:         info.Port,
	}
	
	return int(userID), devInfo, nil
}

// Logout 登出设备
func (s *SDK) Logout(userID int) bool {
	return C.NET_DVR_Logout_V30(C.LONG(userID)) != 0
}

// StartListen 开始监听设备上线
func (s *SDK) StartListen(ip string, port int) (int, error) {
	if !s.initialized {
		return -1, errors.New("SDK未初始化")
	}
	
	cIP := C.CString(ip)
	defer C.free(unsafe.Pointer(cIP))
	
	handle := C.NET_DVR_StartListen_V30(cIP, C.WORD(port), nil, nil)
	if handle < 0 {
		return -1, fmt.Errorf("启动监听失败，错误码: %d", s.GetLastError())
	}
	
	return int(handle), nil
}

// StopListen 停止监听
func (s *SDK) StopListen(handle int) bool {
	return C.NET_DVR_StopListen_V30(C.LONG(handle)) != 0
}

// CapturePicture 抓图
func (s *SDK) CapturePicture(userID, channel int, filename string) error {
	if !s.initialized {
		return errors.New("SDK未初始化")
	}
	
	cFilename := C.CString(filename)
	defer C.free(unsafe.Pointer(cFilename))
	
	var picPara C.NET_DVR_JPEGPARA
	C.memset(unsafe.Pointer(&picPara), 0, C.sizeof_NET_DVR_JPEGPARA)
	picPara.wPicQuality = 2
	picPara.wPicSize = 0
	
	result := C.NET_DVR_CaptureJPEGPicture(C.LONG(userID), C.LONG(channel), &picPara, cFilename)
	if result == 0 {
		return fmt.Errorf("抓图失败，错误码: %d", s.GetLastError())
	}
	
	return nil
}

// CapturePictureToBuffer 抓图到内存
func (s *SDK) CapturePictureToBuffer(userID, channel int) ([]byte, error) {
	// 使用临时文件，然后读取
	// 注意：实际项目中应该使用NET_DVR_CapturePictureBlock_New等支持内存的API
	return nil, errors.New("暂未实现")
}

// RebootDevice 重启设备
func (s *SDK) RebootDevice(userID int) error {
	if !s.initialized {
		return errors.New("SDK未初始化")
	}
	
	result := C.NET_DVR_ControlDevice(C.LONG(userID), C.NET_DVR_REBOOT, nil)
	if result == 0 {
		return fmt.Errorf("重启设备失败，错误码: %d", s.GetLastError())
	}
	
	return nil
}

// PTZControl 云台控制
func (s *SDK) PTZControl(userID, channel int, command int, speed int) error {
	if !s.initialized {
		return errors.New("SDK未初始化")
	}
	
	var ptzControl C.NET_DVR_PTZControl
	C.memset(unsafe.Pointer(&ptzControl), 0, C.sizeof_NET_DVR_PTZControl)
	ptzControl.dwPTZCommand = C.DWORD(command)
	ptzControl.dwStep = C.DWORD(speed)
	ptzControl.bStop = 0
	
	result := C.NET_DVR_PTZControl(C.LONG(userID), C.LONG(channel), C.NET_DVR_PTZ_CONTROL, unsafe.Pointer(&ptzControl), 0)
	if result == 0 {
		return fmt.Errorf("云台控制失败，错误码: %d", s.GetLastError())
	}
	
	return nil
}

// PTZControlStop 停止云台控制
func (s *SDK) PTZControlStop(userID, channel int, command int) error {
	if !s.initialized {
		return errors.New("SDK未初始化")
	}
	
	var ptzControl C.NET_DVR_PTZControl
	C.memset(unsafe.Pointer(&ptzControl), 0, C.sizeof_NET_DVR_PTZControl)
	ptzControl.dwPTZCommand = C.DWORD(command)
	ptzControl.bStop = 1
	
	result := C.NET_DVR_PTZControl(C.LONG(userID), C.LONG(channel), C.NET_DVR_PTZ_CONTROL, unsafe.Pointer(&ptzControl), 0)
	if result == 0 {
		return fmt.Errorf("停止云台控制失败，错误码: %d", s.GetLastError())
	}
	
	return nil
}

// 云台控制命令常量
const (
	PTZ_UP        = 21
	PTZ_DOWN      = 22
	PTZ_LEFT      = 23
	PTZ_RIGHT     = 24
	PTZ_ZOOM_IN   = 11
	PTZ_ZOOM_OUT  = 12
)
