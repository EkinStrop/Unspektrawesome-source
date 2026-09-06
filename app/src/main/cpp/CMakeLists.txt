cmake_minimum_required(VERSION 3.22.1)

project(unspektrawesome_vulkan LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT ANDROID)
    message(FATAL_ERROR "The Unspektrawesome Vulkan library requires the Android NDK")
endif()

if(CMAKE_HOST_WIN32)
    set(GLSLC "${CMAKE_ANDROID_NDK}/shader-tools/windows-x86_64/glslc.exe")
elseif(CMAKE_HOST_APPLE)
    set(GLSLC "${CMAKE_ANDROID_NDK}/shader-tools/darwin-x86_64/glslc")
else()
    set(GLSLC "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
endif()

if(NOT EXISTS "${GLSLC}")
    message(FATAL_ERROR "NDK glslc was not found at ${GLSLC}")
endif()

set(SHADER_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../shaders")
set(SHADER_OUTPUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated/shaders")
file(MAKE_DIRECTORY "${SHADER_OUTPUT_DIR}")

function(embed_shader source_file shader_stage symbol_name output_variable)
    get_filename_component(shader_name "${source_file}" NAME_WE)
    set(spirv_file "${SHADER_OUTPUT_DIR}/${shader_name}_${shader_stage}.spv")
    set(header_file "${SHADER_OUTPUT_DIR}/${shader_name}_${shader_stage}_spv.h")

    add_custom_command(
        OUTPUT "${header_file}"
        COMMAND "${GLSLC}"
            --target-env=vulkan1.1
            -fshader-stage=${shader_stage}
            -I "${SHADER_SOURCE_DIR}"
            -o "${spirv_file}"
            "${SHADER_SOURCE_DIR}/${source_file}"
        COMMAND "${CMAKE_COMMAND}"
            "-DINPUT_FILE=${spirv_file}"
            "-DOUTPUT_FILE=${header_file}"
            "-DSYMBOL_NAME=${symbol_name}"
            -P "${CMAKE_CURRENT_LIST_DIR}/EmbedSpirv.cmake"
        DEPENDS
            "${SHADER_SOURCE_DIR}/${source_file}"
            "${SHADER_SOURCE_DIR}/raw_input.glsl"
            "${SHADER_SOURCE_DIR}/raw_color.glsl"
            "${SHADER_SOURCE_DIR}/frame_geometry.glsl"
            "${SHADER_SOURCE_DIR}/rcd/rcd_common.glsl"
            "${CMAKE_CURRENT_LIST_DIR}/EmbedSpirv.cmake"
        VERBATIM)

    set(${output_variable} "${header_file}" PARENT_SCOPE)
endfunction()

function(embed_shader_variant source_file shader_stage symbol_name variant_name definition output_variable)
    get_filename_component(shader_name "${source_file}" NAME_WE)
    set(spirv_file "${SHADER_OUTPUT_DIR}/${shader_name}_${variant_name}_${shader_stage}.spv")
    set(header_file "${SHADER_OUTPUT_DIR}/${shader_name}_${variant_name}_${shader_stage}_spv.h")

    add_custom_command(
        OUTPUT "${header_file}"
        COMMAND "${GLSLC}"
            --target-env=vulkan1.1
            -fshader-stage=${shader_stage}
            -D${definition}
            -I "${SHADER_SOURCE_DIR}"
            -o "${spirv_file}"
            "${SHADER_SOURCE_DIR}/${source_file}"
        COMMAND "${CMAKE_COMMAND}"
            "-DINPUT_FILE=${spirv_file}"
            "-DOUTPUT_FILE=${header_file}"
            "-DSYMBOL_NAME=${symbol_name}"
            -P "${CMAKE_CURRENT_LIST_DIR}/EmbedSpirv.cmake"
        DEPENDS
            "${SHADER_SOURCE_DIR}/${source_file}"
            "${SHADER_SOURCE_DIR}/raw_input.glsl"
            "${SHADER_SOURCE_DIR}/raw_color.glsl"
            "${SHADER_SOURCE_DIR}/frame_geometry.glsl"
            "${SHADER_SOURCE_DIR}/rcd/rcd_common.glsl"
            "${CMAKE_CURRENT_LIST_DIR}/EmbedSpirv.cmake"
        VERBATIM)

    set(${output_variable} "${header_file}" PARENT_SCOPE)
endfunction()

embed_shader(
    preview_bilinear.comp
    compute
    kPreviewBilinearSpirv
    PREVIEW_SHADER_HEADER)
embed_shader_variant(
    preview_bilinear.comp
    compute
    kPreviewBilinearImageSpirv
    raw_image
    UNSPEKTRA_RAW_IMAGE_INPUT=1
    PREVIEW_IMAGE_SHADER_HEADER)
embed_shader(
    rcd/rcd_guide.comp
    compute
    kRcdGuideBufferSpirv
    RCD_GUIDE_BUFFER_SHADER_HEADER)
embed_shader_variant(
    rcd/rcd_guide.comp
    compute
    kRcdGuideImageSpirv
    raw_image
    UNSPEKTRA_RAW_IMAGE_INPUT=1
    RCD_GUIDE_IMAGE_SHADER_HEADER)
embed_shader(
    rcd/rcd_green.comp
    compute
    kRcdGreenBufferSpirv
    RCD_GREEN_BUFFER_SHADER_HEADER)
embed_shader_variant(
    rcd/rcd_green.comp
    compute
    kRcdGreenImageSpirv
    raw_image
    UNSPEKTRA_RAW_IMAGE_INPUT=1
    RCD_GREEN_IMAGE_SHADER_HEADER)
embed_shader(
    rcd/rcd_median.comp
    compute
    kRcdMedianBufferSpirv
    RCD_MEDIAN_BUFFER_SHADER_HEADER)
embed_shader_variant(
    rcd/rcd_median.comp
    compute
    kRcdMedianImageSpirv
    raw_image
    UNSPEKTRA_RAW_IMAGE_INPUT=1
    RCD_MEDIAN_IMAGE_SHADER_HEADER)
embed_shader(
    rcd/rcd_output.comp
    compute
    kRcdOutputBufferSpirv
    RCD_OUTPUT_BUFFER_SHADER_HEADER)
embed_shader_variant(
    rcd/rcd_output.comp
    compute
    kRcdOutputImageSpirv
    raw_image
    UNSPEKTRA_RAW_IMAGE_INPUT=1
    RCD_OUTPUT_IMAGE_SHADER_HEADER)
embed_shader(
    capture_color.comp
    compute
    kCaptureColorSpirv
    CAPTURE_COLOR_SHADER_HEADER)
embed_shader(
    present.vert
    vertex
    kPresentVertexSpirv
    PRESENT_VERTEX_SHADER_HEADER)
embed_shader(
    present.frag
    fragment
    kPresentFragmentSpirv
    PRESENT_FRAGMENT_SHADER_HEADER)

find_package(Python3 REQUIRED COMPONENTS Interpreter)

set(SPEKTRA_DIR "${CMAKE_CURRENT_LIST_DIR}/spektrafilm")
set(SPEKTRA_SHADER_DIR "${SPEKTRA_DIR}/shaders")
set(SPEKTRA_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated/spektrafilm")
set(SPEKTRA_GENERATED_PROFILE_CURVES
    "${SPEKTRA_GENERATED_DIR}/SpektraGeneratedProfileCurves.cpp")
set(SPEKTRA_GENERATED_PROFILE_COUNTS
    "${SPEKTRA_GENERATED_DIR}/SpektraGeneratedProfileCounts.h")
set(SPEKTRA_GENERATED_HANATOS_LUT
    "${SPEKTRA_GENERATED_DIR}/SpektraHanatos2025Spectra.f32")
set(SPEKTRA_GENERATED_OUTPUT_GAMUT
    "${SPEKTRA_GENERATED_DIR}/SpektraOutputGamutCompression.f32")
set(SPEKTRA_GENERATED_HANATOS_HEADER
    "${SHADER_OUTPUT_DIR}/SpektraHanatos2025Spectra_f32.h")
set(SPEKTRA_GENERATED_OUTPUT_GAMUT_HEADER
    "${SHADER_OUTPUT_DIR}/SpektraOutputGamutCompression_f32.h")
set(SPEKTRA_DATA_DIR "${SPEKTRA_DIR}/Resources/data")

file(GLOB SPEKTRA_PROFILE_JSON CONFIGURE_DEPENDS
    "${SPEKTRA_DATA_DIR}/profiles/*.json"
    "${SPEKTRA_DATA_DIR}/profiles/archive/*.json")
file(GLOB_RECURSE SPEKTRA_RESOURCE_DATA CONFIGURE_DEPENDS
    "${SPEKTRA_DATA_DIR}/filters/*"
    "${SPEKTRA_DATA_DIR}/luts/*")

add_custom_command(
    OUTPUT
        "${SPEKTRA_GENERATED_PROFILE_CURVES}"
        "${SPEKTRA_GENERATED_PROFILE_COUNTS}"
        "${SPEKTRA_GENERATED_HANATOS_LUT}"
        "${SPEKTRA_GENERATED_OUTPUT_GAMUT}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${SPEKTRA_GENERATED_DIR}"
    COMMAND "${CMAKE_COMMAND}" -E env "SPEKTRAFILM_DATA_DIR=${SPEKTRA_DATA_DIR}"
        "${Python3_EXECUTABLE}"
        "${SPEKTRA_DIR}/tools/generate_profile_curves.py"
        --output "${SPEKTRA_GENERATED_PROFILE_CURVES}"
        --counts-output "${SPEKTRA_GENERATED_PROFILE_COUNTS}"
        --hanatos-output "${SPEKTRA_GENERATED_HANATOS_LUT}"
        --output-gamut-compression-output "${SPEKTRA_GENERATED_OUTPUT_GAMUT}"
    DEPENDS
        "${SPEKTRA_DIR}/tools/generate_profile_curves.py"
        "${SPEKTRA_DIR}/tools/ofx_stock_lists.py"
        ${SPEKTRA_PROFILE_JSON}
        ${SPEKTRA_RESOURCE_DATA}
    VERBATIM)

add_custom_command(
    OUTPUT "${SPEKTRA_GENERATED_HANATOS_HEADER}"
    COMMAND "${Python3_EXECUTABLE}"
        "${SPEKTRA_DIR}/tools/bin_to_header.py"
        --input "${SPEKTRA_GENERATED_HANATOS_LUT}"
        --output "${SPEKTRA_GENERATED_HANATOS_HEADER}"
        --var-name SpektraHanatos2025Spectra_f32
    DEPENDS "${SPEKTRA_GENERATED_HANATOS_LUT}"
    VERBATIM)

add_custom_command(
    OUTPUT "${SPEKTRA_GENERATED_OUTPUT_GAMUT_HEADER}"
    COMMAND "${Python3_EXECUTABLE}"
        "${SPEKTRA_DIR}/tools/bin_to_header.py"
        --input "${SPEKTRA_GENERATED_OUTPUT_GAMUT}"
        --output "${SPEKTRA_GENERATED_OUTPUT_GAMUT_HEADER}"
        --var-name SpektraOutputGamutCompression_f32
    DEPENDS "${SPEKTRA_GENERATED_OUTPUT_GAMUT}"
    VERBATIM)

set(SPEKTRA_SHADER_NAMES
    SpektraCopy
    SpektraFormatConvert
    SpektraImageFormatConvert
    SpektraAutoExposure
    SpektraFilmExposure
    SpektraCurveDevelop
    SpektraPrintScan
    SpektraHalation
    SpektraDiffusion
    SpektraDir
    SpektraScannerPost
    SpektraGrain)
set(SPEKTRA_SHADER_HEADERS "")
file(GLOB SPEKTRA_SHADER_INCLUDES CONFIGURE_DEPENDS "${SPEKTRA_SHADER_DIR}/*.glsl")
foreach(SPEKTRA_SHADER_NAME ${SPEKTRA_SHADER_NAMES})
    set(SPEKTRA_SHADER_SOURCE "${SPEKTRA_SHADER_DIR}/${SPEKTRA_SHADER_NAME}.comp")
    set(SPEKTRA_SHADER_SPIRV "${SHADER_OUTPUT_DIR}/${SPEKTRA_SHADER_NAME}.spv")
    set(SPEKTRA_SHADER_HEADER "${SHADER_OUTPUT_DIR}/${SPEKTRA_SHADER_NAME}_spv.h")
    set(SPEKTRA_OPTIMIZATION -O)
    if(SPEKTRA_SHADER_NAME STREQUAL "SpektraPrintScan")
        set(SPEKTRA_OPTIMIZATION -O0)
    endif()
    add_custom_command(
        OUTPUT "${SPEKTRA_SHADER_HEADER}"
        COMMAND "${GLSLC}"
            --target-env=vulkan1.1
            -fshader-stage=compute
            ${SPEKTRA_OPTIMIZATION}
            -I "${SPEKTRA_SHADER_DIR}"
            -o "${SPEKTRA_SHADER_SPIRV}"
            "${SPEKTRA_SHADER_SOURCE}"
        COMMAND "${CMAKE_COMMAND}"
            "-DSPV_FILE=${SPEKTRA_SHADER_SPIRV}"
            "-DHEADER_FILE=${SPEKTRA_SHADER_HEADER}"
            "-DVAR_NAME=${SPEKTRA_SHADER_NAME}_spv"
            -P "${CMAKE_CURRENT_LIST_DIR}/SpektraSpvToHeader.cmake"
        DEPENDS
            "${SPEKTRA_SHADER_SOURCE}"
            ${SPEKTRA_SHADER_INCLUDES}
            "${CMAKE_CURRENT_LIST_DIR}/SpektraSpvToHeader.cmake"
        VERBATIM)
    list(APPEND SPEKTRA_SHADER_HEADERS "${SPEKTRA_SHADER_HEADER}")
endforeach()

list(APPEND SPEKTRA_SHADER_HEADERS
    "${SPEKTRA_GENERATED_HANATOS_HEADER}"
    "${SPEKTRA_GENERATED_OUTPUT_GAMUT_HEADER}")

set_source_files_properties(
    spektrafilm/src/SpektraVulkanRenderer.cpp
    PROPERTIES COMPILE_OPTIONS "-Wno-unused-function")

add_library(
    unspektrawesome_vulkan
    SHARED
    VulkanBridge.cpp
    VulkanRenderer.cpp
    spektrafilm_android_jni.cpp
    spektrafilm/src/SpektraVulkanRenderer.cpp
    "${SPEKTRA_GENERATED_PROFILE_CURVES}"
    ${SPEKTRA_SHADER_HEADERS}
    "${PREVIEW_SHADER_HEADER}"
    "${PREVIEW_IMAGE_SHADER_HEADER}"
    "${RCD_GUIDE_BUFFER_SHADER_HEADER}"
    "${RCD_GUIDE_IMAGE_SHADER_HEADER}"
    "${RCD_GREEN_BUFFER_SHADER_HEADER}"
    "${RCD_GREEN_IMAGE_SHADER_HEADER}"
    "${RCD_MEDIAN_BUFFER_SHADER_HEADER}"
    "${RCD_MEDIAN_IMAGE_SHADER_HEADER}"
    "${RCD_OUTPUT_BUFFER_SHADER_HEADER}"
    "${RCD_OUTPUT_IMAGE_SHADER_HEADER}"
    "${CAPTURE_COLOR_SHADER_HEADER}"
    "${PRESENT_VERTEX_SHADER_HEADER}"
    "${PRESENT_FRAGMENT_SHADER_HEADER}")

target_include_directories(
    unspektrawesome_vulkan
    PRIVATE
    "${SHADER_OUTPUT_DIR}"
    "${SPEKTRA_GENERATED_DIR}"
    "${SPEKTRA_DIR}/src")

find_library(ANDROID_LIBRARY android REQUIRED)
find_library(LOG_LIBRARY log REQUIRED)
find_library(VULKAN_LIBRARY vulkan REQUIRED)

target_link_libraries(
    unspektrawesome_vulkan
    PRIVATE
    "${ANDROID_LIBRARY}"
    "${LOG_LIBRARY}"
    "${VULKAN_LIBRARY}")

target_compile_options(
    unspektrawesome_vulkan
    PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Wno-missing-field-initializers
    -Werror)
