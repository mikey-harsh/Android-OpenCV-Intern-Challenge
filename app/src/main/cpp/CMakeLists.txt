# Sets the minimum version of CMake required.
cmake_minimum_required(VERSION 3.18.1)

# --- 1. Point CMake to your OpenCV SDK ---
# This path is relative to this CMakeLists.txt file.
# It goes up from /app/src/main/cpp/ to /app/ and then into /sdk/native/jni
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../../sdk/native/jni)
message(STATUS "OpenCV SDK path: ${OpenCV_DIR}")

# --- 2. Find the OpenCV Package ---
# This searches for the OpenCV configuration files in the directory specified above.
find_package(OpenCV REQUIRED
        PATHS ${OpenCV_DIR}
        NO_DEFAULT_PATH)

message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")

# --- 3. Add your C++ source file ---
# This is your main JNI file.
add_library(
        native-lib
        SHARED
        native-lib.cpp)

# --- 4. Find system libraries (for logging) ---
find_library(
        log-lib
        log)

# --- 5. Link ALL libraries together ---
# This tells your 'native-lib' to link against the Android log
# and, most importantly, all the required OpenCV libraries.
target_link_libraries(
        native-lib
        ${log-lib}
        ${OpenCV_LIBS} )