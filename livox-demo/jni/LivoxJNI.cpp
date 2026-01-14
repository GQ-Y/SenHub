#include "LivoxJNI.h"
#include "livox_lidar_api.h"
#include "livox_lidar_def.h"
#include <string>
#include <jni.h>

// Global callback object
static jobject g_callback_obj = nullptr;
static JavaVM* g_jvm = nullptr;

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

    // Get callback class and method
    jclass callback_class = env->FindClass("com/livox/demo/PointCloudCallback");
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

JNIEXPORT jboolean JNICALL Java_com_livox_demo_LivoxJNI_init
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
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_livox_demo_LivoxJNI_start
  (JNIEnv *env, jclass clazz) {
    return LivoxLidarSdkStart() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_livox_demo_LivoxJNI_stop
  (JNIEnv *env, jclass clazz) {
    LivoxLidarSdkUninit();
    
    // Cleanup callback reference
    if (g_callback_obj != nullptr) {
        env->DeleteGlobalRef(g_callback_obj);
        g_callback_obj = nullptr;
    }
}

JNIEXPORT void JNICALL Java_com_livox_demo_LivoxJNI_setPointCloudCallback
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
