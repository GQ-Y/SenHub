#include "LivoxJNI.h"
#include "livox_lidar_api.h"
#include "livox_lidar_def.h"
#include <string>
#include <cstring>
#include <map>
#include <mutex>
#include <jni.h>

// Global callback objects
static jobject g_pointcloud_callback_obj = nullptr;
static jobject g_deviceinfo_callback_obj = nullptr;
static JavaVM* g_jvm = nullptr;

// Device info cache: handle -> (serial, ip)
static std::map<uint32_t, std::pair<std::string, std::string>> g_device_cache;
static std::map<std::string, uint32_t> g_ip_to_handle;
static std::mutex g_cache_mutex;

// Helper: Get JNI environment
static JNIEnv* getJNIEnv(bool& attached) {
    JNIEnv* env = nullptr;
    attached = false;
    
    if (g_jvm == nullptr) {
        return nullptr;
    }
    
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return nullptr;
        }
    }
    return env;
}

// Point cloud callback from Livox SDK
void PointCloudCallback(uint32_t handle, const uint8_t dev_type, 
                        LivoxLidarEthernetPacket* data, void* client_data) {
    if (data == nullptr || g_pointcloud_callback_obj == nullptr) {
        return;
    }

    bool attached = false;
    JNIEnv* env = getJNIEnv(attached);
    if (env == nullptr) {
        return;
    }

    // Get callback class and method
    jclass callback_class = env->FindClass("com/digital/video/gateway/driver/livox/PointCloudCallback");
    if (callback_class == nullptr) {
        if (attached) g_jvm->DetachCurrentThread();
        return;
    }

    jmethodID method_id = env->GetMethodID(callback_class, "onPointCloud", "(IIII[B)V");
    if (method_id == nullptr) {
        env->DeleteLocalRef(callback_class);
        if (attached) g_jvm->DetachCurrentThread();
        return;
    }

    // Create byte array for point cloud data
    jbyteArray data_array = env->NewByteArray(data->length);
    if (data_array != nullptr) {
        env->SetByteArrayRegion(data_array, 0, data->length, (jbyte*)data->data);
    }

    // Call Java callback
    env->CallVoidMethod(g_pointcloud_callback_obj, method_id,
        (jint)handle,
        (jint)dev_type,
        (jint)data->dot_num,
        (jint)data->data_type,
        data_array);

    // Cleanup
    if (data_array != nullptr) {
        env->DeleteLocalRef(data_array);
    }
    env->DeleteLocalRef(callback_class);

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

// Device info change callback from Livox SDK
void DeviceInfoChangeCallback(const uint32_t handle, const LivoxLidarInfo* info, void* client_data) {
    if (info == nullptr) {
        return;
    }
    
    // Cache device info
    // Note: info->sn is char[16], info->lidar_ip is char[16] (string format like "192.168.1.120")
    std::string serial(info->sn);
    std::string ip(info->lidar_ip);
    
    {
        std::lock_guard<std::mutex> lock(g_cache_mutex);
        g_device_cache[handle] = std::make_pair(serial, ip);
        g_ip_to_handle[ip] = handle;
    }
    
    // Call Java callback if set
    if (g_deviceinfo_callback_obj == nullptr) {
        return;
    }
    
    bool attached = false;
    JNIEnv* env = getJNIEnv(attached);
    if (env == nullptr) {
        return;
    }

    jclass callback_class = env->FindClass("com/digital/video/gateway/driver/livox/DeviceInfoCallback");
    if (callback_class == nullptr) {
        if (attached) g_jvm->DetachCurrentThread();
        return;
    }

    jmethodID method_id = env->GetMethodID(callback_class, "onDeviceInfoChange", 
        "(IILjava/lang/String;Ljava/lang/String;)V");
    if (method_id == nullptr) {
        env->DeleteLocalRef(callback_class);
        if (attached) g_jvm->DetachCurrentThread();
        return;
    }

    jstring jserial = env->NewStringUTF(serial.c_str());
    jstring jip = env->NewStringUTF(ip.c_str());

    env->CallVoidMethod(g_deviceinfo_callback_obj, method_id,
        (jint)handle,
        (jint)info->dev_type,
        jserial,
        jip);

    env->DeleteLocalRef(jserial);
    env->DeleteLocalRef(jip);
    env->DeleteLocalRef(callback_class);

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

JNIEXPORT jboolean JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_init
  (JNIEnv *env, jclass clazz, jstring config_path) {
    
    // Store JVM reference
    env->GetJavaVM(&g_jvm);
    
    // Convert Java string to C string
    const char* path = env->GetStringUTFChars(config_path, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }

    // Initialize Livox SDK
    bool result = LivoxLidarSdkInit(path, "");
    
    env->ReleaseStringUTFChars(config_path, path);
    
    // Register device info callback
    if (result) {
        SetLivoxLidarInfoChangeCallback(DeviceInfoChangeCallback, nullptr);
    }
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_start
  (JNIEnv *env, jclass clazz) {
    return LivoxLidarSdkStart() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_stop
  (JNIEnv *env, jclass clazz) {
    LivoxLidarSdkUninit();
    
    // Cleanup callback references
    if (g_pointcloud_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_pointcloud_callback_obj);
        g_pointcloud_callback_obj = nullptr;
    }
    if (g_deviceinfo_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_deviceinfo_callback_obj);
        g_deviceinfo_callback_obj = nullptr;
    }
    
    // Clear cache
    {
        std::lock_guard<std::mutex> lock(g_cache_mutex);
        g_device_cache.clear();
        g_ip_to_handle.clear();
    }
}

JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_setPointCloudCallback
  (JNIEnv *env, jclass clazz, jobject callback) {
    
    // Release old callback if exists
    if (g_pointcloud_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_pointcloud_callback_obj);
    }
    
    // Create global reference to new callback
    if (callback != nullptr) {
        g_pointcloud_callback_obj = env->NewGlobalRef(callback);
        SetLivoxLidarPointCloudCallBack(PointCloudCallback, nullptr);
    } else {
        g_pointcloud_callback_obj = nullptr;
        SetLivoxLidarPointCloudCallBack(nullptr, nullptr);
    }
}

JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_setDeviceInfoCallback
  (JNIEnv *env, jclass clazz, jobject callback) {
    
    // Release old callback if exists
    if (g_deviceinfo_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_deviceinfo_callback_obj);
    }
    
    // Create global reference to new callback
    if (callback != nullptr) {
        g_deviceinfo_callback_obj = env->NewGlobalRef(callback);
    } else {
        g_deviceinfo_callback_obj = nullptr;
    }
}

JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceSerial
  (JNIEnv *env, jclass clazz, jint handle) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    auto it = g_device_cache.find((uint32_t)handle);
    if (it != g_device_cache.end()) {
        return env->NewStringUTF(it->second.first.c_str());
    }
    return nullptr;
}

JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceIp
  (JNIEnv *env, jclass clazz, jint handle) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    auto it = g_device_cache.find((uint32_t)handle);
    if (it != g_device_cache.end()) {
        return env->NewStringUTF(it->second.second.c_str());
    }
    return nullptr;
}

JNIEXPORT jintArray JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getConnectedDeviceHandles
  (JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    int size = g_device_cache.size();
    jintArray result = env->NewIntArray(size);
    if (result == nullptr || size == 0) {
        return result;
    }
    
    jint* handles = new jint[size];
    int i = 0;
    for (auto& pair : g_device_cache) {
        handles[i++] = (jint)pair.first;
    }
    
    env->SetIntArrayRegion(result, 0, size, handles);
    delete[] handles;
    
    return result;
}

JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceSerialByIp
  (JNIEnv *env, jclass clazz, jstring ip) {
    if (ip == nullptr) {
        return nullptr;
    }
    
    const char* ip_str = env->GetStringUTFChars(ip, nullptr);
    if (ip_str == nullptr) {
        return nullptr;
    }
    
    std::string ip_cpp(ip_str);
    env->ReleaseStringUTFChars(ip, ip_str);
    
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    auto it = g_ip_to_handle.find(ip_cpp);
    if (it != g_ip_to_handle.end()) {
        auto dev_it = g_device_cache.find(it->second);
        if (dev_it != g_device_cache.end()) {
            return env->NewStringUTF(dev_it->second.first.c_str());
        }
    }
    
    return nullptr;
}
