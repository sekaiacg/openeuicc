#pragma once
#include <euicc/euicc.h>
#include <pthread.h>
#include <jni.h>

struct lpac_jni_ctx {
    jobject apdu_interface;
    jobject http_interface;
};

#define LPAC_JNI_CTX(ctx) ((struct lpac_jni_ctx *) ctx->userdata)

extern JavaVM *jvm;