/*
 * Minimal copy of AOSP libcutils native_handle.h for NDK builds.
 * The NDK does not ship this header; it is required for
 * AHardwareBuffer_getNativeHandle() return type and accessing numFds/data.
 * Layout must match system/core/libcutils/include/cutils/native_handle.h.
 */
#ifndef WINLATOR_NATIVE_HANDLE_H
#define WINLATOR_NATIVE_HANDLE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct native_handle {
    int version;   /* sizeof(native_handle_t) */
    int numFds;    /* number of file descriptors at data[0] */
    int numInts;   /* number of ints at data[numFds] */
    int data[0];   /* numFds + numInts ints */
} native_handle_t;

#ifdef __cplusplus
}
#endif

#endif /* WINLATOR_NATIVE_HANDLE_H */
