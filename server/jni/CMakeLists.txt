cmake_minimum_required(VERSION 3.10)
project(LivoxJNI)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Java
find_package(Java REQUIRED)
find_package(JNI REQUIRED)

# Include directories
# Try to find SDK in multiple possible locations
set(POSSIBLE_SDK_PATHS
    ${CMAKE_CURRENT_SOURCE_DIR}/../livox-sdk2-official
    ${CMAKE_CURRENT_SOURCE_DIR}/../../livox-demo/livox-sdk2-official
    /home/zyc/data/xwq/livox-demo/livox-sdk2-official
)

foreach(SDK_PATH ${POSSIBLE_SDK_PATHS})
    if(EXISTS ${SDK_PATH}/include/livox_lidar_api.h)
        set(SDK_ROOT ${SDK_PATH})
        break()
    endif()
endforeach()

if(NOT SDK_ROOT)
    message(FATAL_ERROR "Cannot find Livox SDK. Please ensure livox-sdk2-official is available.")
endif()

message(STATUS "Using Livox SDK at: ${SDK_ROOT}")

include_directories(
    ${JNI_INCLUDE_DIRS}
    ${SDK_ROOT}/include
    ${SDK_ROOT}/sdk_core
)

# Link directories
link_directories(/usr/local/lib)
# SDK library should be in lib/linux directory
link_directories(${CMAKE_CURRENT_SOURCE_DIR}/../lib/linux)

# Source files
set(SOURCES
    LivoxJNI.cpp
)

# Create shared library
add_library(livoxjni SHARED ${SOURCES})

# Link libraries
# Use full path to SDK library
set(SDK_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../lib/linux")
target_link_directories(livoxjni PRIVATE ${SDK_LIB_DIR})
target_link_libraries(livoxjni
    ${JNI_LIBRARIES}
    livox_lidar_sdk_shared
    pthread
)

# Set output directory and library name
set_target_properties(livoxjni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../lib/linux
    SUFFIX ".so"
)
