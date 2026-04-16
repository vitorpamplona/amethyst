add_executable(secp256k1_bench benchmark.c)

target_link_libraries(secp256k1_bench PRIVATE schnorr256k1)

# Benchmark uses internal SHA-256 functions directly
target_include_directories(secp256k1_bench PRIVATE
    ${CMAKE_SOURCE_DIR}/src
)

if(UNIX)
    target_link_libraries(secp256k1_bench PRIVATE m)
endif()
