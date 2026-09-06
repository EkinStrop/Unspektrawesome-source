# SpvToHeader.cmake - Convert SPIR-V binary to C header with uint32_t array
# Usage: cmake -DSPV_FILE=foo.spv -DHEADER_FILE=foo_spv.h -DVAR_NAME=foo_spv -P SpvToHeader.cmake

file(READ "${SPV_FILE}" SPV_HEX HEX)
string(LENGTH "${SPV_HEX}" SPV_HEX_LEN)
math(EXPR SPV_BYTE_COUNT "${SPV_HEX_LEN} / 2")
math(EXPR SPV_WORD_COUNT "${SPV_BYTE_COUNT} / 4")

set(HEADER "// Auto-generated SPIR-V - DO NOT EDIT\n")
string(APPEND HEADER "#pragma once\n")
string(APPEND HEADER "#include <cstdint>\n\n")
string(APPEND HEADER "static const uint32_t ${VAR_NAME}[] = {\n")

set(WORD_IDX 0)
set(LINE "    ")
while(WORD_IDX LESS SPV_WORD_COUNT)
    math(EXPR BYTE_OFFSET "${WORD_IDX} * 8")
    string(SUBSTRING "${SPV_HEX}" ${BYTE_OFFSET} 8 WORD_HEX)
    # Little-endian bytes to uint32: b0 b1 b2 b3 → 0xb3b2b1b0
    string(SUBSTRING "${WORD_HEX}" 0 2 B0)
    string(SUBSTRING "${WORD_HEX}" 2 2 B1)
    string(SUBSTRING "${WORD_HEX}" 4 2 B2)
    string(SUBSTRING "${WORD_HEX}" 6 2 B3)
    string(APPEND LINE "0x${B3}${B2}${B1}${B0}")

    math(EXPR WORD_IDX "${WORD_IDX} + 1")
    if(WORD_IDX LESS SPV_WORD_COUNT)
        string(APPEND LINE ", ")
    endif()

    math(EXPR MOD "${WORD_IDX} % 8")
    if(MOD EQUAL 0 AND WORD_IDX LESS SPV_WORD_COUNT)
        string(APPEND LINE "\n    ")
    endif()
endwhile()

string(APPEND HEADER "${LINE}\n};\n\n")
string(APPEND HEADER "static const size_t ${VAR_NAME}_size = sizeof(${VAR_NAME});\n")

file(WRITE "${HEADER_FILE}" "${HEADER}")
