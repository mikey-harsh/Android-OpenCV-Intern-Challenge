#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

// Store a persistent reference to the output byte array
static jbyteArray processedData = nullptr;
static cv::Mat processedMat;

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_edgedetectionapp_MainActivity_processFrame(
        JNIEnv *env,
        jclass clazz,
        jint width, jint height,
        jbyteArray yuv_data) {

    // 1. Convert JNI byte array (YUV) to an OpenCV Mat
    jbyte *yuv = env->GetByteArrayElements(yuv_data, 0);
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (unsigned char *) yuv);
    cv::Mat grayMat;

    // 2. Convert YUV to Grayscale
    // The Canny algorithm needs a grayscale image
    cv::cvtColor(yuvMat, grayMat, cv::COLOR_YUV2GRAY_NV21);

    // 3. Apply Canny Edge Detection
    cv::Mat edgesMat;
    cv::Canny(grayMat, edgesMat, 100, 200);

    // 4. Convert Grayscale Edges to RGBA
    // OpenGL needs an RGBA texture, so we convert the 1-channel
    // Canny output to a 4-channel RGBA image.
    if (processedMat.rows != height || processedMat.cols != width) {
        processedMat = cv::Mat(height, width, CV_8UC4);
    }
    cv::cvtColor(edgesMat, processedMat, cv::COLOR_GRAY2RGBA);

    // 5. Convert processed Mat back to a jbyteArray
    int totalBytes = processedMat.total() * processedMat.elemSize();

    // Re-use the global byte array if possible
    if (processedData == nullptr || env->GetArrayLength(processedData) != totalBytes) {
        if (processedData != nullptr) {
            env->DeleteGlobalRef(processedData);
        }
        jbyteArray newProcessedData = env->NewByteArray(totalBytes);
        processedData = (jbyteArray) env->NewGlobalRef(newProcessedData);
        env->DeleteLocalRef(newProcessedData);
    }

    // Copy data from Mat to the byte array
    jbyte *processed_bytes = env->GetByteArrayElements(processedData, 0);
    memcpy(processed_bytes, processedMat.data, totalBytes);
    env->ReleaseByteArrayElements(processedData, processed_bytes, 0);

    // Release the input array
    env->ReleaseByteArrayElements(yuv_data, yuv, 0);

    // Return the processed data
    return processedData;
}