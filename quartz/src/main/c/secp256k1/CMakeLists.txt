cmake_minimum_required(VERSION 3.18)
project(secp256k1_amethyst C)

set(CMAKE_C_STANDARD 11)

# ==================== Platform-specific optimizations ====================

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    message(STATUS "ARM64 detected - enabling NEON and crypto extensions")
    set(PLATFORM_FLAGS "-march=armv8-a+crypto -O3 -fomit-frame-pointer")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|amd64")
    message(STATUS "x86_64 detected - enabling BMI2 and ADX")
    set(PLATFORM_FLAGS "-march=x86-64-v2 -mbmi2 -O3 -fomit-frame-pointer")
else()
    message(STATUS "Generic platform - using portable implementation")
    set(PLATFORM_FLAGS "-O3 -fomit-frame-pointer")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${PLATFORM_FLAGS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter")

# ==================== Library sources ====================

set(SECP256K1_SOURCES
    field.c
    scalar.c
    point.c
    schnorr.c
    sha256.c
)

# Static library
add_library(secp256k1_amethyst STATIC ${SECP256K1_SOURCES})
target_include_directories(secp256k1_amethyst PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# Shared library (for JNI)
add_library(secp256k1_amethyst_jni SHARED ${SECP256K1_SOURCES})
target_include_directories(secp256k1_amethyst_jni PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# ==================== JNI bridge ====================

if(JNI_INCLUDE_DIR)
    target_sources(secp256k1_amethyst_jni PRIVATE jni_bridge.c)
    target_include_directories(secp256k1_amethyst_jni PRIVATE ${JNI_INCLUDE_DIR})
    if(JNI_INCLUDE_DIR_PLATFORM)
        target_include_directories(secp256k1_amethyst_jni PRIVATE ${JNI_INCLUDE_DIR_PLATFORM})
    endif()
endif()

# ==================== Android NDK build ====================

if(ANDROID)
    # Android-specific: build shared library for JNI
    find_library(log-lib log)
    target_link_libraries(secp256k1_amethyst_jni ${log-lib})

    # Include JNI headers from NDK
    target_sources(secp256k1_amethyst_jni PRIVATE jni_bridge.c)
endif()

# ==================== Standalone benchmark ====================

if(NOT ANDROID)
    add_executable(secp256k1_bench benchmark.c)
    target_link_libraries(secp256k1_bench secp256k1_amethyst)
    if(UNIX)
        target_link_libraries(secp256k1_bench m)
    endif()
endif()
