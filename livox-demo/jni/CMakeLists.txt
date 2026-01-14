cmake_minimum_required(VERSION 3.10)
project(LivoxJNI)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# macOS specific settings
if(APPLE)
    # Get macOS SDK path
    execute_process(
        COMMAND xcrun --show-sdk-path
        OUTPUT_VARIABLE MACOS_SDK_PATH
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
    if(MACOS_SDK_PATH)
        message(STATUS "Found macOS SDK: ${MACOS_SDK_PATH}")
        set(CMAKE_OSX_SYSROOT ${MACOS_SDK_PATH})
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -isysroot ${MACOS_SDK_PATH}")
    else()
        # Fallback: use default SDK
        message(WARNING "Could not find macOS SDK path, using default")
    endif()
    # Use libc++ standard library
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -stdlib=libc++")
endif()

# Find Java
find_package(Java REQUIRED)
find_package(JNI REQUIRED)

# Include directories
include_directories(
    ${JNI_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/../livox-sdk2-official/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../livox-sdk2-official/sdk_core
)

# macOS: Add C++ standard library include path
if(APPLE)
    include_directories(
        /Library/Developer/CommandLineTools/usr/include/c++/v1
    )
endif()

# Link directories
link_directories(/usr/local/lib)
link_directories(${CMAKE_CURRENT_SOURCE_DIR}/../livox-sdk2-official/build/sdk_core)

# Source files
set(SOURCES
    LivoxJNI.cpp
)

# Create shared library
add_library(livoxjni SHARED ${SOURCES})

# Set compile options for macOS
if(APPLE)
    # Add C++ standard library include paths
    # Both paths are needed: one in SDK, one in CommandLineTools
    target_include_directories(livoxjni PRIVATE
        ${MACOS_SDK_PATH}/usr/include/c++/v1
        /Library/Developer/CommandLineTools/usr/include/c++/v1
    )
    target_compile_options(livoxjni PRIVATE 
        -isysroot ${MACOS_SDK_PATH}
        -stdlib=libc++
    )
endif()

# Link libraries
# Use full path to SDK library
if(APPLE)
    # macOS: use lib from lib/macos directory
    set(SDK_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../lib/macos")
else()
    # Linux: use lib from SDK build directory
    set(SDK_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../livox-sdk2-official/build/sdk_core")
endif()
target_link_directories(livoxjni PRIVATE ${SDK_LIB_DIR})
target_link_libraries(livoxjni
    ${JNI_LIBRARIES}
    livox_lidar_sdk_shared
    pthread
)

# Set output directory and library name based on platform
if(APPLE)
    set_target_properties(livoxjni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../lib
        SUFFIX ".dylib"
    )
else()
    set_target_properties(livoxjni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../lib
        SUFFIX ".so"
    )
endif()
