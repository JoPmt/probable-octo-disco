cmake_minimum_required(VERSION 3.22.1)
project("llama_agent")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8-a+simd+dotprod)
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_HAVE_DOTPROD)
endif()

set(CMAKE_CXX_FLAGS_RELEASE "-O3 -Ofast -fomit-frame-pointer -flto")

add_library(${PROJECT_NAME} SHARED
    jni_mapping_bridge.cpp
    llama_agent_core.cpp
    grammar_rules.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(${PROJECT_NAME}
    ${log-lib}
    ${android-lib}
)
