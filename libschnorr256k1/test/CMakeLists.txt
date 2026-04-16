add_executable(schnorr256k1_tests
    test_main.c
    test_schnorr.c
    test_secp256k1.c
    test_field.c
    test_point.c
    test_key_codec.c
    test_sha256.c
)

target_link_libraries(schnorr256k1_tests PRIVATE schnorr256k1)

# Tests need access to internal headers for white-box testing of
# field arithmetic, point operations, and key codec internals.
target_include_directories(schnorr256k1_tests PRIVATE
    ${CMAKE_SOURCE_DIR}/src
)

if(UNIX)
    target_link_libraries(schnorr256k1_tests PRIVATE m)
endif()

add_test(NAME secp256k1_tests COMMAND schnorr256k1_tests)
