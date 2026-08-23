cmake_minimum_required(VERSION 3.22.1)
project(tgk_uinput LANGUAGES CXX)

add_library(tgk_uinput SHARED tgk_uinput.cpp)

target_compile_features(tgk_uinput PRIVATE cxx_std_17)
target_link_libraries(tgk_uinput log)
