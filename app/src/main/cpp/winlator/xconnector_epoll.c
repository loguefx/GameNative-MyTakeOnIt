#include <jni.h>
#include <sys/epoll.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/eventfd.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <malloc.h>
#include <jni.h>
#include <android/log.h>
#include <android/fdsan.h>
#include <sys/resource.h>
#include <errno.h>

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__);
#define MAX_EVENTS 10
#define MAX_FDS 32

#define MAX_TRACKED_FDS 1024  // Adjust based on your needs

typedef struct {
    int fd;
    bool is_owned;
} FdTracker;

static FdTracker fd_tracking[MAX_TRACKED_FDS] = {0};

/*
 * NOTE: events[] is declared inside doEpollIndefinitely (stack-allocated per-call)
 * so concurrent XConnectorEpoll threads (X server, ALSA, SHM…) each have their own
 * copy.  A file-scope global here would be a data race across those threads.
 */

// Call this when you first obtain/create a file descriptor
void trackFd(jint fd) {
    for (int i = 0; i < MAX_TRACKED_FDS; i++) {
        if (fd_tracking[i].fd == 0) {
            fd_tracking[i].fd = fd;
            fd_tracking[i].is_owned = true;
            break;
        }
    }
}
void closeFd(jint fd) {
    bool can_close = false;

    // Find and check ownership
    for (int i = 0; i < MAX_TRACKED_FDS; i++) {
        if (fd_tracking[i].fd == fd) {
            if (fd_tracking[i].is_owned) {
                can_close = true;
                // Mark as no longer owned
                fd_tracking[i].fd = 0;
                fd_tracking[i].is_owned = false;
            }
            break;
        }
    }

    if (can_close) {
        close(fd);
        printf("XConnectorEpoll close %d", fd);
    } else {
        printf("XConnectorEpoll attempted to close unowned fd %d", fd);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_setRLimitToMax(JNIEnv *env, jobject obj) {
    struct rlimit rlm;
    if (getrlimit(RLIMIT_NOFILE, &rlm) < 0) {
        printf("XConnectorEpoll failed to getrlimit %s", strerror(errno));
    } else {
        printf("XConnectorEpoll setting current limit (%lu) to max limit (%lu)", rlm.rlim_cur, rlm.rlim_max);
        rlm.rlim_cur = rlm.rlim_max;
        if (setrlimit(RLIMIT_NOFILE, &rlm) < 0) {
            printf("XConnectorEpoll failed to setrlimit %s", strerror(errno));
        }
    }
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_createAFUnixSocket(JNIEnv *env, jobject obj,
                                                                jstring path) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    printf("xconnector_epoll.c socket %d", fd);
    if (fd < 0) return -1;
    trackFd(fd);

    struct sockaddr_un serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sun_family = AF_LOCAL;

    const char *pathPtr = (*env)->GetStringUTFChars(env, path, 0);

    int addrLength = sizeof(sa_family_t) + strlen(pathPtr);
    strncpy(serverAddr.sun_path, pathPtr, sizeof(serverAddr.sun_path) - 1);

    (*env)->ReleaseStringUTFChars(env, path, pathPtr);

    unlink(serverAddr.sun_path);
    if (bind(fd, (struct sockaddr*) &serverAddr, addrLength) < 0) goto error;
    if (listen(fd, MAX_EVENTS) < 0) goto error;

    return fd;
    error:
    closeFd(fd);
    return -1;
}

/*
 * Bind a Linux abstract-namespace Unix socket.
 * Wine's Xlib/XCB always tries the abstract socket \0/tmp/.X11-unix/X0 FIRST,
 * before falling back to the filesystem path.  Binding this abstract socket
 * lets winex11.drv connect without needing a real /tmp on the Android host.
 *
 * "name" is the socket name WITHOUT the leading null byte, e.g. "/tmp/.X11-unix/X0".
 * The kernel distinguishes abstract sockets by sun_path[0] == '\0'.
 */
JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_createAbstractAFUnixSocket(JNIEnv *env, jobject obj,
                                                                         jstring name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    printf("xconnector_epoll.c abstract socket %d", fd);
    if (fd < 0) return -1;
    trackFd(fd);

    const char *namePtr = (*env)->GetStringUTFChars(env, name, 0);
    size_t nameLen = strlen(namePtr);

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    /* sun_path[0] = '\0' signals abstract namespace; name follows immediately */
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, namePtr, sizeof(addr.sun_path) - 2);
    /* address length: sa_family + '\0' byte + name (no null terminator counted) */
    socklen_t addrLen = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + nameLen);

    (*env)->ReleaseStringUTFChars(env, name, namePtr);

    if (bind(fd, (struct sockaddr*)&addr, addrLen) < 0) {
        printf("xconnector_epoll.c abstract bind failed: %s", strerror(errno));
        closeFd(fd);
        return -1;
    }
    if (listen(fd, MAX_EVENTS) < 0) {
        printf("xconnector_epoll.c abstract listen failed: %s", strerror(errno));
        closeFd(fd);
        return -1;
    }
    printf("xconnector_epoll.c abstract socket bound to \\0%s", addr.sun_path + 1);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_createEpollFd(JNIEnv *env, jobject obj) {
    int fd = epoll_create(MAX_EVENTS);
    printf("xconnector_epoll.c epoll_create %d", fd);
    trackFd(fd);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_closeFd(JNIEnv *env, jobject obj, jint fd) {
    closeFd(fd);
}

/*
 * doEpollIndefinitely: wait for one epoll batch, dispatch new-connection or data events.
 * acceptFromFd: helper that accepts one client from a server fd and notifies Java.
 */
static void acceptFromFd(JNIEnv *env, jobject obj, jclass cls,
                         jint epollFd, jint listenFd,
                         jboolean addClientToEpoll) {
    jmethodID handleNewConnection = (*env)->GetMethodID(env, cls, "handleNewConnection", "(I)V");
    int clientFd = accept(listenFd, NULL, NULL);
    printf("xconnector_epoll.c accept %d from listenFd %d", clientFd, listenFd);
    if (clientFd < 0) return;
    trackFd(clientFd);
    if (addClientToEpoll) {
        struct epoll_event ev = {.data.fd = clientFd, .events = EPOLLIN};
        if (epoll_ctl(epollFd, EPOLL_CTL_ADD, clientFd, &ev) >= 0) {
            (*env)->CallVoidMethod(env, obj, handleNewConnection, clientFd);
        }
    } else {
        (*env)->CallVoidMethod(env, obj, handleNewConnection, clientFd);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_doEpollIndefinitely(JNIEnv *env, jobject obj,
                                                                 jint epollFd,
                                                                 jint serverFd,
                                                                 jboolean addClientToEpoll,
                                                                 jint abstractServerFd) {
    /*
     * abstractServerFd is passed directly from Java (not looked up via GetFieldID)
     * so this function is safe under ProGuard / R8 obfuscation.  A value of -1
     * means no abstract socket is in use.
     */
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID handleExistingConnection =
            (*env)->GetMethodID(env, cls, "handleExistingConnection", "(I)V");

    /* Stack-local so each XConnectorEpoll thread has its own buffer (no data race). */
    struct epoll_event events[MAX_EVENTS];
    int numFds = epoll_wait(epollFd, events, MAX_EVENTS, -1);
    for (int i = 0; i < numFds; i++) {
        int evFd = events[i].data.fd;
        if (evFd == serverFd) {
            acceptFromFd(env, obj, cls, epollFd, serverFd, addClientToEpoll);
        } else if (abstractServerFd >= 0 && evFd == abstractServerFd) {
            /* New connection on the abstract-namespace socket (winex11.drv path). */
            acceptFromFd(env, obj, cls, epollFd, abstractServerFd, addClientToEpoll);
        } else if (events[i].events & EPOLLIN) {
            (*env)->CallVoidMethod(env, obj, handleExistingConnection, evFd);
        }
    }
    return numFds >= 0;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_addFdToEpoll(JNIEnv *env, jobject obj,
                                                          jint epollFd,
                                                          jint fd) {
    struct epoll_event event;
    event.data.fd = fd;
    event.events = EPOLLIN;
    if (epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, &event) < 0) return JNI_FALSE;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_removeFdFromEpoll(JNIEnv *env, jobject obj,
                                                               jint epollFd, jint fd) {
    epoll_ctl(epollFd, EPOLL_CTL_DEL, fd, NULL);
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_ClientSocket_read(JNIEnv *env, jobject obj, jint fd, jobject data,
                                               jint offset, jint length) {
    char *dataAddr = (*env)->GetDirectBufferAddress(env, data);
    return read(fd, dataAddr + offset, length);
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_ClientSocket_write(JNIEnv *env, jobject obj, jint fd, jobject data,
                                                jint length) {
//    printf("Writing to %d", fd);
    char *dataAddr = (*env)->GetDirectBufferAddress(env, data);
    return write(fd, dataAddr, length);
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_createEventFd(JNIEnv *env, jobject obj) {
    int fd = eventfd(0, EFD_NONBLOCK);
    printf("xconnector_epoll.c eventfd %d", fd);
    trackFd(fd);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_ClientSocket_recvAncillaryMsg(JNIEnv *env, jobject obj, jint clientFd, jobject data,
                                                           jint offset, jint length) {
    char *dataAddr = (*env)->GetDirectBufferAddress(env, data);

    struct iovec iovmsg = {.iov_base = dataAddr + offset, .iov_len = length};
    struct {
        struct cmsghdr align;
        int fds[MAX_FDS];
    } ctrlmsg;

    struct msghdr msg = {
        .msg_name = NULL,
        .msg_namelen = 0,
        .msg_iov = &iovmsg,
        .msg_iovlen = 1,
        .msg_control = &ctrlmsg,
        .msg_controllen = sizeof(struct cmsghdr) + MAX_FDS * sizeof(int)
    };

    int size = recvmsg(clientFd, &msg, 0);

    if (size >= 0) {
        struct cmsghdr *cmsg;
        for (cmsg = CMSG_FIRSTHDR(&msg); cmsg; cmsg = CMSG_NXTHDR(&msg, cmsg)) {
            if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS) {
                int numFds = (cmsg->cmsg_len - CMSG_LEN(0)) / sizeof(int);
                if (numFds > 0) {
                    jclass cls = (*env)->GetObjectClass(env, obj);
                    jmethodID addAncillaryFd = (*env)->GetMethodID(env, cls, "addAncillaryFd", "(I)V");
                    for (int i = 0; i < numFds; i++) {
                        int ancillaryFd = ((int*)CMSG_DATA(cmsg))[i];
                        printf("xconnector_epoll.c CMSG_DATA %d", ancillaryFd);
                        trackFd(ancillaryFd);
                        (*env)->CallVoidMethod(env, obj, addAncillaryFd, ancillaryFd);
                    }
                }
            }
        }
    }
    return size;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xconnector_ClientSocket_sendAncillaryMsg(JNIEnv *env, jobject obj, jint clientFd,
                                                           jobject data, jint length, jint ancillaryFd) {
    char *dataAddr = (*env)->GetDirectBufferAddress(env, data);

    struct iovec iovmsg = {.iov_base = dataAddr, .iov_len = length};
    struct {
        struct cmsghdr align;
        int fds[1];
    } ctrlmsg;

    struct msghdr msg = {
        .msg_name = NULL,
        .msg_namelen = 0,
        .msg_iov = &iovmsg,
        .msg_iovlen = 1,
        .msg_flags = 0,
        .msg_control = &ctrlmsg,
        .msg_controllen = sizeof(struct cmsghdr) + sizeof(int)
    };

    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = msg.msg_controllen;
    ((int*)CMSG_DATA(cmsg))[0] = ancillaryFd;

    jint size = sendmsg(clientFd, &msg, 0);
    printf("xconnector_epoll.c sendmsg size %d", size);
    return size;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_waitForSocketRead(JNIEnv *env, jobject obj, jint clientFd, jint shutdownFd) {
    struct pollfd pfds[2];
    pfds[0].fd = clientFd;
    pfds[0].events = POLLIN;

    pfds[1].fd = shutdownFd;
    pfds[1].events = POLLIN;

    int res = poll(pfds, 2, -1);
    if (res < 0 || (pfds[1].revents & POLLIN)) return JNI_FALSE;

    if (pfds[0].revents & POLLIN) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        jmethodID handleExistingConnection = (*env)->GetMethodID(env, cls, "handleExistingConnection", "(I)V");
        (*env)->CallVoidMethod(env, obj, handleExistingConnection, clientFd);
    }
    return JNI_TRUE;
}

JNIEXPORT jintArray JNICALL
Java_com_winlator_xconnector_XConnectorEpoll_pollEpollEvents(JNIEnv *env, jobject obj,
                                                             jint epollFd, jint maxEvents) {
    struct epoll_event events[maxEvents];
    int numFds = epoll_wait(epollFd, events, maxEvents, -1); // Wait indefinitely

    if (numFds < 0) return NULL;

    jintArray result = (*env)->NewIntArray(env, numFds);
    if (result == NULL) return NULL;

    jint *r = (*env)->GetIntArrayElements(env, result, 0);

    for (int i = 0; i < numFds; i++) {
        r[i] = events[i].data.fd; // Store file descriptor
    }

    (*env)->ReleaseIntArrayElements(env, result, r, 0);
    return result;
}
