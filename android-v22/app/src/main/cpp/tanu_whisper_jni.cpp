#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <string>
#include <vector>
#include <thread>
#include "whisper.h"

struct Holder { whisper_context *ctx=nullptr; };

extern "C" JNIEXPORT jlong JNICALL Java_com_tanu_personal_transcription_NativeWhisper_nativeInitModel(JNIEnv *env,jclass,jobject assetManager,jstring assetName){
    AAssetManager *mgr=AAssetManager_fromJava(env,assetManager); if(!mgr)return 0;
    const char *name=env->GetStringUTFChars(assetName,nullptr); AAsset *asset=AAssetManager_open(mgr,name,AASSET_MODE_BUFFER); env->ReleaseStringUTFChars(assetName,name); if(!asset)return 0;
    const void *data=AAsset_getBuffer(asset); size_t len=(size_t)AAsset_getLength64(asset); if(!data||len<1000000){AAsset_close(asset);return 0;}
    whisper_context_params cp=whisper_context_default_params();cp.use_gpu=false;auto *h=new Holder();h->ctx=whisper_init_from_buffer_with_params((void*)data,len,cp);AAsset_close(asset);if(!h->ctx){delete h;return 0;}return reinterpret_cast<jlong>(h);
}
extern "C" JNIEXPORT jstring JNICALL Java_com_tanu_personal_transcription_NativeWhisper_nativeTranscribe(JNIEnv *env,jclass,jlong handle,jshortArray audio,jstring prompt){
    auto *h=reinterpret_cast<Holder*>(handle);if(!h||!h->ctx)return env->NewStringUTF("");jsize n=env->GetArrayLength(audio);if(n<1600)return env->NewStringUTF("");
    std::vector<jshort> s((size_t)n);env->GetShortArrayRegion(audio,0,n,s.data());std::vector<float> pcm((size_t)n);for(int i=0;i<n;i++)pcm[(size_t)i]=s[(size_t)i]/32768.0f;
    const char *pp=env->GetStringUTFChars(prompt,nullptr);std::string ptxt=pp?pp:"";if(pp)env->ReleaseStringUTFChars(prompt,pp);
    whisper_full_params p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);p.translate=true;p.language="auto";p.n_threads=std::max(1,std::min(4,(int)std::thread::hardware_concurrency()));p.no_context=false;p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.suppress_blank=true;if(!ptxt.empty())p.initial_prompt=ptxt.c_str();
    if(whisper_full(h->ctx,p,pcm.data(),(int)pcm.size())!=0)return env->NewStringUTF("");std::string out;int count=whisper_full_n_segments(h->ctx);for(int i=0;i<count;i++){const char *t=whisper_full_get_segment_text(h->ctx,i);if(t){out+=t;out+=' ';}}return env->NewStringUTF(out.c_str());
}
extern "C" JNIEXPORT void JNICALL Java_com_tanu_personal_transcription_NativeWhisper_nativeFree(JNIEnv*,jclass,jlong handle){auto *h=reinterpret_cast<Holder*>(handle);if(h){if(h->ctx)whisper_free(h->ctx);delete h;}}
