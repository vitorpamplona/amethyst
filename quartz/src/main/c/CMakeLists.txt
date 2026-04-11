# Root CMakeLists for Android NDK builds.
# Called from quartz/build.gradle.kts via android { externalNativeBuild { cmake { } } }
cmake_minimum_required(VERSION 3.18)
project(secp256k1_amethyst C)

add_subdirectory(secp256k1)
