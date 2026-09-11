cmake_minimum_required(VERSION 3.22.1)

project(whisper_thecut C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

file(READ "${WHISPER_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "1.7.1")
endif()

message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(SOURCE_FILES
    ${WHISPER_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper-jni.c
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

include(FetchContent)

function(build_whisper_lib target_name)
    add_library(
        ${target_name}
        SHARED
        ${SOURCE_FILES}
    )

    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        set(GGML_COMPILE_OPTIONS                      -march=armv8.2-a+fp16)
    elseif (${target_name} STREQUAL "whisper_vfpv4")
        target_compile_options(${target_name} PRIVATE -mfpu=neon-vfpv4)
        set(GGML_COMPILE_OPTIONS                      -mfpu=neon-vfpv4)
    endif ()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3)
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)
        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
    endif ()

    FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
    FetchContent_MakeAvailable(ggml)
    if (DEFINED GGML_COMPILE_OPTIONS)
        target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})
    endif()
    target_link_libraries(${target_name} ${LOG_LIB} ${ANDROID_LIB} ggml)
endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_whisper_lib("whisper_v8fp16_va")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_whisper_lib("whisper_vfpv4")
endif ()

build_whisper_lib("whisper")

include_directories(${WHISPER_DIR})
include_directories(${WHISPER_DIR}/src)
include_directories(${WHISPER_DIR}/include)
include_directories(${WHISPER_DIR}/ggml/include)
include_directories(${WHISPER_DIR}/ggml/src)
include_directories(${WHISPER_DIR}/ggml/src/ggml-cpu)
