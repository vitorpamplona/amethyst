# jsonBench C/C++ harness. Third-party sources come from jsonBench/.cache/cpp (fetch.sh,
# pinned in deps.txt); run.sh fetches, configures, builds and runs.
cmake_minimum_required(VERSION 3.20)
project(jsonbench_cpp C CXX)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_C_STANDARD 11)
set(JB_OPT_FLAGS "-O3 -march=native")
set(CMAKE_C_FLAGS_RELEASE "${JB_OPT_FLAGS} -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "${JB_OPT_FLAGS} -DNDEBUG")

set(JB_CACHE "${CMAKE_CURRENT_SOURCE_DIR}/../.cache/cpp" CACHE PATH "fetched third-party sources")
get_filename_component(JB_CACHE "${JB_CACHE}" ABSOLUTE)

# deps.txt -> JB_REF_<name>="<tag>" (a commit pin is reported as master@<12 hex>)
set(JB_REF_DEFS "")
file(STRINGS "${CMAKE_CURRENT_SOURCE_DIR}/deps.txt" JB_DEP_LINES REGEX "^[^#]")
foreach(line IN LISTS JB_DEP_LINES)
  string(REGEX REPLACE "[ \t]+" ";" parts "${line}")
  list(GET parts 0 name)
  list(GET parts 2 ref)
  if(NOT EXISTS "${JB_CACHE}/${name}")
    message(FATAL_ERROR "missing ${JB_CACHE}/${name}: run jsonBench/cpp/fetch.sh")
  endif()
  if(ref MATCHES "^[0-9a-f]+$" AND NOT ref MATCHES "^[0-9]+$")
    string(SUBSTRING "${ref}" 0 12 short)
    set(ref "master@${short}")
  endif()
  string(REPLACE "-" "_" key "${name}")
  list(APPEND JB_REF_DEFS "JB_REF_${key}=\"${ref}\"")
endforeach()

find_package(OpenSSL REQUIRED)

# --- C libraries -----------------------------------------------------------------------
add_library(jb_simdjson STATIC "${JB_CACHE}/simdjson/singleheader/simdjson.cpp")
target_include_directories(jb_simdjson SYSTEM PUBLIC "${JB_CACHE}/simdjson/singleheader")

add_library(jb_yyjson STATIC "${JB_CACHE}/yyjson/src/yyjson.c")
target_include_directories(jb_yyjson SYSTEM PUBLIC "${JB_CACHE}/yyjson/src")

add_library(jb_cjson STATIC "${JB_CACHE}/cjson/cJSON.c")
target_include_directories(jb_cjson SYSTEM PUBLIC "${JB_CACHE}/cjson")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_APPS OFF CACHE BOOL "" FORCE)
set(DISABLE_WERROR ON CACHE BOOL "" FORCE)
set(DISABLE_EXTRA_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory("${JB_CACHE}/jsonc" jsonc EXCLUDE_FROM_ALL)

set(JANSSON_EXAMPLES OFF CACHE BOOL "" FORCE)
set(JANSSON_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(JANSSON_WITHOUT_TESTS ON CACHE BOOL "" FORCE)
set(JANSSON_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${JB_CACHE}/jansson" jansson EXCLUDE_FROM_ALL)
# json-c and Jansson both export json_object_get and json_object_iter_next: rename Jansson's.
set(JB_JANSSON_RENAMES json_object_get=jansson_object_get json_object_iter_next=jansson_object_iter_next)
target_compile_definitions(jansson PRIVATE ${JB_JANSSON_RENAMES})
set_source_files_properties(src/p_jansson.cpp PROPERTIES COMPILE_DEFINITIONS "${JB_JANSSON_RENAMES}")

# --- harness ---------------------------------------------------------------------------
add_executable(jsonbench
  src/main.cpp
  src/alloc_count.c
  src/p_simdjson.cpp
  src/p_yyjson.cpp
  src/p_rapidjson.cpp
  src/p_nlohmann.cpp
  src/p_glaze.cpp
  src/p_cjson.cpp
  src/p_boostjson.cpp
  src/p_sajson.cpp
  src/p_jsonc.cpp
  src/p_jsmn.cpp
  src/p_jansson.cpp)

set(JB_BOOST_INCLUDES "")
foreach(lib json align assert compat config container container_hash core describe endian intrusive move mp11
            predef static_assert system throw_exception variant2 winapi)
  list(APPEND JB_BOOST_INCLUDES "${JB_CACHE}/boost-${lib}/include")
endforeach()

target_include_directories(jsonbench PRIVATE src)
target_include_directories(jsonbench SYSTEM PRIVATE
  "${JB_CACHE}/rapidjson/include"
  "${JB_CACHE}/nlohmann/single_include"
  "${JB_CACHE}/glaze/include"
  "${JB_CACHE}/sajson/include"
  "${JB_CACHE}/jsmn"
  ${JB_BOOST_INCLUDES})

execute_process(COMMAND ${CMAKE_CXX_COMPILER} --version OUTPUT_VARIABLE JB_CXX_VERSION OUTPUT_STRIP_TRAILING_WHITESPACE)
string(REGEX REPLACE "\n.*" "" JB_CXX_VERSION "${JB_CXX_VERSION}")
target_compile_definitions(jsonbench PRIVATE
  ${JB_REF_DEFS}
  "JB_COMPILER=\"${JB_CXX_VERSION}\""
  "JB_FLAGS=\"${JB_OPT_FLAGS}\""
  RAPIDJSON_SSE42
  RAPIDJSON_HAS_STDSTRING=1
  BOOST_JSON_NO_LIB BOOST_CONTAINER_NO_LIB BOOST_ALL_NO_LIB)

# json-c's static target is json-c-static when shared is also on; we force static only.
target_link_libraries(jsonbench PRIVATE jb_simdjson jb_yyjson jb_cjson json-c jansson OpenSSL::Crypto)
# Export the interposed malloc family so shared libraries (libstdc++, libcrypto) bind to it.
target_link_options(jsonbench PRIVATE -rdynamic)
