cmake_minimum_required(VERSION 3.18)
project(schnorr256k1 C)

set(CMAKE_C_STANDARD 11)

# ==================== Options ====================

option(BUILD_TESTS "Build tests" ON)
option(BUILD_BENCH "Build benchmarks" ON)

# ==================== Link-Time Optimization ====================
#
# LTO lets the linker inline fe_mul / fe_sqr across translation units
# (field.c <-> point.c <-> scalar.c <-> schnorr.c), recovering ~5-15% on the
# hot Jacobian formulas where fe_mul would otherwise be a call boundary.
# Disabled automatically when the toolchain doesn't support it.

include(CheckIPOSupported)
check_ipo_supported(RESULT LTO_SUPPORTED OUTPUT LTO_ERROR)
if(LTO_SUPPORTED)
    message(STATUS "LTO supported - enabling interprocedural optimization")
    set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
else()
    message(STATUS "LTO not supported: ${LTO_ERROR}")
endif()

# ==================== Platform-specific optimizations ====================

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    message(STATUS "ARM64 detected - enabling NEON and crypto extensions")
    set(PLATFORM_FLAGS "-march=armv8-a+crypto -O3 -fomit-frame-pointer")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|amd64")
    message(STATUS "x86_64 detected - enabling BMI2 and ADX")
    set(PLATFORM_FLAGS "-march=x86-64-v2 -mbmi2 -msha -msse4.1 -O3 -fomit-frame-pointer")
else()
    message(STATUS "Generic platform - using portable implementation")
    set(PLATFORM_FLAGS "-O3 -fomit-frame-pointer")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${PLATFORM_FLAGS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter")

# ==================== Core static library ====================

set(SECP256K1_SOURCES
    src/field.c
    src/scalar.c
    src/point.c
    src/schnorr.c
    src/sha256.c
)

add_library(schnorr256k1 STATIC ${SECP256K1_SOURCES})
target_include_directories(schnorr256k1
    PUBLIC  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
            $<INSTALL_INTERFACE:include>
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src
)

# ==================== Optional components ====================

if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(test)
endif()

if(BUILD_BENCH)
    add_subdirectory(bench)
endif()

# ==================== Install rules ====================

install(TARGETS schnorr256k1
    ARCHIVE DESTINATION lib
    LIBRARY DESTINATION lib
)
install(FILES include/schnorr256k1.h DESTINATION include)
