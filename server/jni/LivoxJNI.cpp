#include "LivoxJNI.h"
#include "livox_lidar_api.h"
#include "livox_lidar_def.h"
#include <string>
#include <jni.h>
#include <map>
#include <mutex>

// Global callback objects
static jobject g_callback_obj = nullptr;
static jobject g_device_info_callback_obj = nullptr;
static JavaVM* g_jvm = nullptr;

// Device info cache: handle -> (sn, ip)
static std::map<uint32_t, std::pair<std::string, std::string>> g_device_info_cache;
static std::mutex g_device_info_mutex;

// Device info change callback from Livox SDK
void DeviceInfoChangeCallback(const uint32_t handle, const LivoxLidarInfo* info, void* client_data) {
    if (info == nullptr) {
        return;
    }

    // Cache device info
    {
        std::lock_guard<std::mutex> lock(g_device_info_mutex);
        g_device_info_cache[handle] = std::make_pair(std::string(info->sn), std::string(info->lidar_ip));
    }

    if (g_device_info_callback_obj == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    
    // Get JNI environment
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }

    if (env == nullptr) {
        return;
    }

    // Get callback class and method
    jclass callback_class = env->FindClass("com/digital/video/gateway/driver/livox/DeviceInfoCallback");
    if (callback_class == nullptr) {
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }

    jmethodID method_id = env->GetMethodID(callback_class, "onDeviceInfoChange", 
        "(IILjava/lang/String;Ljava/lang/String;)V");
    if (method_id == nullptr) {
        env->DeleteLocalRef(callback_class);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }

    // Create Java strings for SN and IP
    jstring sn_str = env->NewStringUTF(info->sn);
    jstring ip_str = env->NewStringUTF(info->lidar_ip);

    // Call Java callback
    env->CallVoidMethod(g_device_info_callback_obj, method_id,
        (jint)handle,
        (jint)info->dev_type,
        sn_str,
        ip_str);

    // Cleanup
    if (sn_str != nullptr) {
        env->DeleteLocalRef(sn_str);
    }
    if (ip_str != nullptr) {
        env->DeleteLocalRef(ip_str);
    }
    env->DeleteLocalRef(callback_class);

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

// Point cloud callback from Livox SDK
void PointCloudCallback(uint32_t handle, const uint8_t dev_type, 
                        LivoxLidarEthernetPacket* data, void* client_data) {
    if (data == nullptr || g_callback_obj == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    
    // Get JNI environment
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }

    if (env == nullptr) {
        return;
    }

    // Get callback class and method - Updated package name
    jclass callback_class = env->FindClass("com/digital/video/gateway/driver/livox/PointCloudCallback");
    if (callback_class == nullptr) {
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }

    jmethodID method_id = env->GetMethodID(callback_class, "onPointCloud", 
        "(IIII[B)V");
    if (method_id == nullptr) {
        env->DeleteLocalRef(callback_class);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }

    // Create byte array for point cloud data
    jbyteArray data_array = env->NewByteArray(data->length);
    if (data_array != nullptr) {
        env->SetByteArrayRegion(data_array, 0, data->length, 
                                (jbyte*)data->data);
    }

    // Call Java callback
    env->CallVoidMethod(g_callback_obj, method_id,
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

// Updated JNI method name for new package: com.digital.video.gateway.driver.livox
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
    
    if (result) {
        // Register device info change callback
        SetLivoxLidarInfoChangeCallback(DeviceInfoChangeCallback, nullptr);
    }
    
    return result ? JNI_TRUE : JNI_FALSE;
}

// Updated JNI method name
JNIEXPORT jboolean JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_start
  (JNIEnv *env, jclass clazz) {
    return LivoxLidarSdkStart() ? JNI_TRUE : JNI_FALSE;
}

// Updated JNI method name
JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_stop
  (JNIEnv *env, jclass clazz) {
    LivoxLidarSdkUninit();
    
    // Cleanup callback references
    if (g_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_callback_obj);
        g_callback_obj = nullptr;
    }
    if (g_device_info_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_device_info_callback_obj);
        g_device_info_callback_obj = nullptr;
    }
    
    // Clear device info cache
    {
        std::lock_guard<std::mutex> lock(g_device_info_mutex);
        g_device_info_cache.clear();
    }
}

// Updated JNI method name
JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_setPointCloudCallback
  (JNIEnv *env, jclass clazz, jobject callback) {
    
    // Release old callback if exists
    if (g_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_callback_obj);
    }
    
    // Create global reference to new callback
    if (callback != nullptr) {
        g_callback_obj = env->NewGlobalRef(callback);
        SetLivoxLidarPointCloudCallBack(PointCloudCallback, nullptr);
    } else {
        g_callback_obj = nullptr;
        SetLivoxLidarPointCloudCallBack(nullptr, nullptr);
    }
}

// Set device info callback
JNIEXPORT void JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_setDeviceInfoCallback
  (JNIEnv *env, jclass clazz, jobject callback) {
    
    // Release old callback if exists
    if (g_device_info_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_device_info_callback_obj);
    }
    
    // Create global reference to new callback
    if (callback != nullptr) {
        g_device_info_callback_obj = env->NewGlobalRef(callback);
    } else {
        g_device_info_callback_obj = nullptr;
    }
}

// Get device serial number by handle
JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceSerial
  (JNIEnv *env, jclass clazz, jint handle) {
    std::lock_guard<std::mutex> lock(g_device_info_mutex);
    auto it = g_device_info_cache.find((uint32_t)handle);
    if (it != g_device_info_cache.end()) {
        return env->NewStringUTF(it->second.first.c_str());
    }
    return nullptr;
}

// Get device IP by handle
JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceIp
  (JNIEnv *env, jclass clazz, jint handle) {
    std::lock_guard<std::mutex> lock(g_device_info_mutex);
    auto it = g_device_info_cache.find((uint32_t)handle);
    if (it != g_device_info_cache.end()) {
        return env->NewStringUTF(it->second.second.c_str());
    }
    return nullptr;
}

// Get all connected device handles
JNIEXPORT jintArray JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getConnectedDeviceHandles
  (JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_device_info_mutex);
    
    int count = g_device_info_cache.size();
    jintArray result = env->NewIntArray(count);
    if (result == nullptr || count == 0) {
        return result;
    }
    
    jint* handles = new jint[count];
    int i = 0;
    for (auto& pair : g_device_info_cache) {
        handles[i++] = (jint)pair.first;
    }
    
    env->SetIntArrayRegion(result, 0, count, handles);
    delete[] handles;
    
    return result;
}

// Get device serial by IP (search cache)
JNIEXPORT jstring JNICALL Java_com_digital_video_gateway_driver_livox_LivoxJNI_getDeviceSerialByIp
  (JNIEnv *env, jclass clazz, jstring ip) {
    if (ip == nullptr) {
        return nullptr;
    }
    
    const char* ip_str = env->GetStringUTFChars(ip, nullptr);
    if (ip_str == nullptr) {
        return nullptr;
    }
    
    std::string target_ip(ip_str);
    env->ReleaseStringUTFChars(ip, ip_str);
    
    std::lock_guard<std::mutex> lock(g_device_info_mutex);
    for (auto& pair : g_device_info_cache) {
        if (pair.second.second == target_ip) {
            return env->NewStringUTF(pair.second.first.c_str());
        }
    }
    
    return nullptr;
}
