#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/java/com/tanu/app app/src/main/cpp app/src/main/res/values app/src/main/assets/models
cat > settings.gradle <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name='TANU'; include ':app'
EOF
cat > build.gradle <<'EOF'
plugins { id 'com.android.application' version '8.7.3' apply false }
EOF
cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=false
EOF
cat > app/build.gradle <<'EOF'
plugins { id 'com.android.application' }

android {
  namespace 'com.tanu.app'
  compileSdk 35
  ndkVersion '27.2.12479018'
  defaultConfig {
    applicationId 'com.tanu.app'
    minSdk 26
    targetSdk 35
    versionCode 1
    versionName '1.0'
    ndk { abiFilters 'arm64-v8a' }
    externalNativeBuild { cmake { cppFlags '-std=c++17 -O3'; arguments '-DANDROID_STL=c++_shared','-DWHISPER_BUILD_TESTS=OFF','-DWHISPER_BUILD_EXAMPLES=OFF','-DGGML_NATIVE=OFF','-DGGML_OPENMP=OFF' } }
  }
  externalNativeBuild { cmake { path file('src/main/cpp/CMakeLists.txt'); version '3.22.1' } }
  androidResources { noCompress 'bin' }
  compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
}
EOF
cat > app/src/main/AndroidManifest.xml <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-feature android:name="android.hardware.microphone" android:required="true" />
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <application android:theme="@style/AppTheme" android:label="TANU" android:allowBackup="false" android:usesCleartextTraffic="false">
    <activity android:name=".MainActivity" android:screenOrientation="portrait" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>
EOF
cat > app/src/main/res/values/styles.xml <<'EOF'
<resources><style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar"><item name="android:fontFamily">sans</item><item name="android:windowLightStatusBar">true</item><item name="android:colorAccent">#1E6B5C</item></style></resources>
EOF
cat > app/src/main/cpp/CMakeLists.txt <<'EOF'
cmake_minimum_required(VERSION 3.22.1)
project(tanu_whisper)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory(whispercpp)
add_library(tanu-whisper SHARED tanu_whisper_jni.cpp)
target_include_directories(tanu-whisper PRIVATE whispercpp/include)
target_link_libraries(tanu-whisper whisper log android)
EOF
cat > app/src/main/cpp/tanu_whisper_jni.cpp <<'EOF'
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_tanu_app_MainActivity_nativeTranscribe(JNIEnv *env, jclass, jstring modelPath, jfloatArray audio) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) return env->NewStringUTF("ERROR: Could not load speech model");

    jsize n = env->GetArrayLength(audio);
    std::vector<float> pcm((size_t)n);
    env->GetFloatArrayRegion(audio, 0, n, pcm.data());

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.translate = true;
    p.language = "auto";
    p.n_threads = std::max(1, std::min(4, (int)std::thread::hardware_concurrency()));
    p.no_context = true;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;

    if (whisper_full(ctx, p, pcm.data(), (int)pcm.size()) != 0) {
        whisper_free(ctx);
        return env->NewStringUTF("ERROR: Transcription failed");
    }
    std::string out;
    int count = whisper_full_n_segments(ctx);
    for (int i=0;i<count;i++) { out += whisper_full_get_segment_text(ctx, i); out += "\n"; }
    whisper_free(ctx);
    return env->NewStringUTF(out.c_str());
}
EOF
cat > app/src/main/java/com/tanu/app/MainActivity.java <<'EOF'
package com.tanu.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  static { System.loadLibrary("tanu-whisper"); }
  public static native String nativeTranscribe(String modelPath, float[] audio);
  final int MIC=7; LinearLayout root; MediaRecorder recorder; File audioFile; EditText title, participants; TextView status; long started;
  final SimpleDateFormat fmt=new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

  @Override public void onCreate(Bundle b){ super.onCreate(b); if(hasAccount()) home(); else auth(); }
  TextView tv(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setPadding(8,10,8,10); return v; }
  Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setPadding(8,8,8,8); return b; }
  void base(String heading){ root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,36,28,24); ScrollView sv=new ScrollView(this); sv.addView(root); setContentView(sv); TextView h=tv(heading,28); h.setTypeface(null,1); root.addView(h); }
  EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); root.addView(e,new LinearLayout.LayoutParams(-1,-2)); return e; }
  void note(String s){ root.addView(tv(s,14)); }

  boolean hasAccount(){ return getPreferences(0).getBoolean("account",false); }
  void auth(){ base("TANU"); note("Transcription • Actions • Notes • Updates\nPrivate meeting intelligence processed on this phone."); EditText n=input("Your name"); EditText em=input("Email or mobile"); EditText pw=input("Create password"); pw.setInputType(0x81); Button c=btn("Create local account"); root.addView(c); c.setOnClickListener(v->{ if(n.getText().toString().trim().isEmpty()||pw.length()<4){ toast("Enter name and password (4+ characters)"); return;} getPreferences(0).edit().putBoolean("account",true).putString("name",n.getText().toString().trim()).putString("login",em.getText().toString().trim()).putString("pass",hash(pw.getText().toString())).apply(); home();}); note("No server is needed for this local account. Guests do not need a TANU ID."); }

  void home(){ base("TANU"); String name=getPreferences(0).getString("name",""); note("Welcome, "+name+"\nOffline mode • Multilingual speech • English MOM"); Button start=btn("Start a meeting"); Button history=btn("My meetings"); Button privacy=btn("Privacy & storage"); root.addView(start); root.addView(history); root.addView(privacy); start.setOnClickListener(v->meetingForm()); history.setOnClickListener(v->history()); privacy.setOnClickListener(v->privacy()); }

  void meetingForm(){ base("New meeting"); Button back=btn("← Home"); root.addView(back,0); back.setOnClickListener(v->home()); title=input("Meeting title"); participants=input("Participants / guests, separated by commas"); note("Anyone can be added as a guest. They do not need TANU or an account."); status=tv("Ready. Recording is compressed to save space.",15); root.addView(status); Button record=btn("Start recording"); root.addView(record); record.setOnClickListener(v->{ if(recorder==null) startRecording(record); else stopAndProcess(record); }); }

  void startRecording(Button b){ if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC); return; } try { File d=new File(getFilesDir(),"audio"); d.mkdirs(); audioFile=new File(d,"meeting_"+System.currentTimeMillis()+".m4a"); recorder=new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioChannels(1); recorder.setAudioSamplingRate(16000); recorder.setAudioEncodingBitRate(24000); recorder.setOutputFile(audioFile.getAbsolutePath()); recorder.prepare(); recorder.start(); started=System.currentTimeMillis(); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); status.setText("● Recording… You may speak in Tamil, English, Hindi, Telugu, Malayalam, Kannada or other supported languages."); b.setText("Stop & create English MOM"); } catch(Exception e){ toast("Recording error: "+e.getMessage()); recorder=null; } }
  void stopAndProcess(Button b){ try{ recorder.stop(); recorder.release(); }catch(Exception ignored){} recorder=null; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); b.setEnabled(false); status.setText("Processing entirely on this phone…"); final String meetingTitle=title.getText().toString().trim().isEmpty()?"Meeting":title.getText().toString().trim(); final String people=participants.getText().toString().trim(); new Thread(()->{ try { String model=ensureModel(); runOnUiThread(()->status.setText("1/3 Decoding compressed audio locally…")); float[] pcm=decode(audioFile); runOnUiThread(()->status.setText("2/3 Transcribing & translating speech to English…")); String transcript=nativeTranscribe(model,pcm); if(transcript.startsWith("ERROR:")) throw new Exception(transcript); runOnUiThread(()->status.setText("3/3 Creating English Minutes of Meeting…")); String mom=makeMom(meetingTitle,people,transcript); File saved=saveMeeting(meetingTitle,mom,transcript); runOnUiThread(()->showResult(saved,mom,transcript)); }catch(Exception e){ runOnUiThread(()->{status.setText("Could not process: "+e.getMessage()); b.setEnabled(true);}); }}).start(); }

  String ensureModel() throws Exception { File m=new File(getFilesDir(),"ggml-tiny-q5_1.bin"); if(m.exists()&&m.length()>20_000_000) return m.getAbsolutePath(); try(InputStream in=getAssets().open("models/ggml-tiny-q5_1.bin"); OutputStream out=new FileOutputStream(m)){ byte[] buf=new byte[65536]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); } return m.getAbsolutePath(); }

  float[] decode(File f) throws Exception { MediaExtractor ex=new MediaExtractor(); ex.setDataSource(f.getAbsolutePath()); int track=-1; MediaFormat format=null; for(int i=0;i<ex.getTrackCount();i++){ MediaFormat x=ex.getTrackFormat(i); String mime=x.getString(MediaFormat.KEY_MIME); if(mime!=null&&mime.startsWith("audio/")){track=i;format=x;break;} } if(track<0) throw new Exception("No audio track"); ex.selectTrack(track); String mime=format.getString(MediaFormat.KEY_MIME); MediaCodec codec=MediaCodec.createDecoderByType(mime); codec.configure(format,null,null,0); codec.start(); FloatCollector fc=new FloatCollector(); MediaCodec.BufferInfo info=new MediaCodec.BufferInfo(); boolean inDone=false,outDone=false; int sampleRate=format.containsKey(MediaFormat.KEY_SAMPLE_RATE)?format.getInteger(MediaFormat.KEY_SAMPLE_RATE):16000; int channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1; while(!outDone){ if(!inDone){ int ix=codec.dequeueInputBuffer(10000); if(ix>=0){ ByteBuffer ib=codec.getInputBuffer(ix); int n=ex.readSampleData(ib,0); if(n<0){codec.queueInputBuffer(ix,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}else{codec.queueInputBuffer(ix,0,n,ex.getSampleTime(),0);ex.advance();}} } int ox=codec.dequeueOutputBuffer(info,10000); if(ox==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){ MediaFormat of=codec.getOutputFormat(); if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE); if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT); } else if(ox>=0){ ByteBuffer ob=codec.getOutputBuffer(ox); if(ob!=null&&info.size>0){ ob.position(info.offset); ob.limit(info.offset+info.size); ob.order(ByteOrder.LITTLE_ENDIAN); ShortBuffer sb=ob.asShortBuffer(); while(sb.remaining()>=channels){ float sum=0; for(int c=0;c<channels;c++) sum+=sb.get()/32768f; fc.add(sum/channels); } } codec.releaseOutputBuffer(ox,false); if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) outDone=true; } } codec.stop(); codec.release(); ex.release(); float[] raw=fc.array(); if(sampleRate==16000) return raw; int outN=(int)((long)raw.length*16000/sampleRate); float[] r=new float[outN]; for(int i=0;i<outN;i++){ float src=i*(sampleRate/16000f); int a=Math.min(raw.length-1,(int)src); int z=Math.min(raw.length-1,a+1); float t=src-a; r[i]=raw[a]*(1-t)+raw[z]*t; } return r; }
  static class FloatCollector { float[] a=new float[65536]; int n=0; void add(float v){ if(n==a.length)a=Arrays.copyOf(a,a.length*2); a[n++]=v;} float[] array(){return Arrays.copyOf(a,n);} }

  String makeMom(String t,String people,String transcript){ String clean=transcript.replaceAll("\\s+"," ").trim(); String[] ss=clean.split("(?<=[.!?])\\s+"); List<String> decisions=new ArrayList<>(),actions=new ArrayList<>(),pending=new ArrayList<>(); String[] names=people.split(","); Pattern due=Pattern.compile("(?i)\\bby\\s+([A-Za-z0-9 ,/-]{2,25})"); for(String s:ss){ String l=s.toLowerCase(Locale.ENGLISH); if(l.contains("decided")||l.contains("agreed")||l.contains("approved")||l.contains("finalized")||l.contains("confirmed")) decisions.add(s); if(l.contains(" will ")||l.contains(" must ")||l.contains(" need to ")||l.contains(" should ")||l.contains("action")){ String owner="Unassigned"; for(String nm:names){ String x=nm.trim(); if(!x.isEmpty()&&l.contains(x.toLowerCase(Locale.ENGLISH))){owner=x;break;} } Matcher m=due.matcher(s); String d=m.find()?m.group(1).trim():"Not specified"; actions.add(s+" | Owner: "+owner+" | Due: "+d); } if(l.contains("pending")||l.contains("follow up")||l.contains("follow-up")||l.contains("not confirmed")) pending.add(s); } StringBuilder summary=new StringBuilder(); for(int i=0;i<Math.min(4,ss.length);i++) if(!ss[i].isBlank()) summary.append(ss[i]).append(' '); return "MINUTES OF MEETING\n\nTitle: "+t+"\nDate: "+fmt.format(new Date())+"\nParticipants: "+(people.isBlank()?"Not specified":people)+"\n\nEXECUTIVE SUMMARY\n"+summary.toString().trim()+"\n\nKEY DECISIONS\n"+bullets(decisions,"No explicit decisions detected.")+"\n\nACTION ITEMS\n"+bullets(actions,"No explicit action items detected.")+"\n\nPENDING ITEMS\n"+bullets(pending,"No explicit pending items detected.")+"\n\nNEXT STEP\nReview this MOM, correct any names/dates if needed, then approve and share.\n"; }
  String bullets(List<String> x,String none){ if(x.isEmpty()) return "• "+none; StringBuilder b=new StringBuilder(); for(String s:x)b.append("• ").append(s.trim()).append("\n"); return b.toString().trim(); }

  File saveMeeting(String title,String mom,String transcript)throws Exception{ File d=new File(getFilesDir(),"meetings");d.mkdirs(); File f=new File(d,System.currentTimeMillis()+".txt"); try(Writer w=new OutputStreamWriter(new FileOutputStream(f),"UTF-8")){w.write(mom+"\n\n--- ORIGINAL TANU ENGLISH TRANSCRIPT ---\n"+transcript);} return f; }
  void showResult(File f,String mom,String transcript){ base("MOM ready"); TextView m=tv(mom,15); m.setTextIsSelectable(true); root.addView(m); Button approve=btn("Approve MOM & delete audio"); Button share=btn("Share MOM"); Button home=btn("Home"); root.addView(approve);root.addView(share);root.addView(home); approve.setOnClickListener(v->{ if(audioFile!=null&&audioFile.exists())audioFile.delete(); toast("Approved. Recording deleted to save space."); approve.setEnabled(false);}); share.setOnClickListener(v->shareText(mom)); home.setOnClickListener(v->home()); }
  void history(){ base("My meetings"); Button back=btn("← Home"); root.addView(back,0); back.setOnClickListener(v->home()); File d=new File(getFilesDir(),"meetings"); File[] fs=d.listFiles((x,n)->n.endsWith(".txt")); if(fs==null||fs.length==0){note("No meetings yet.");return;} Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified())); for(File f:fs){ Button b=btn(fmt.format(new Date(f.lastModified())));root.addView(b);b.setOnClickListener(v->openMeeting(f)); } }
  void openMeeting(File f){ try{ String all=read(f); String mom=all.split("--- ORIGINAL TANU ENGLISH TRANSCRIPT ---")[0].trim(); base("Saved meeting"); TextView x=tv(mom,15);x.setTextIsSelectable(true);root.addView(x);Button s=btn("Share MOM");Button b=btn("← Meetings");root.addView(s);root.addView(b);s.setOnClickListener(v->shareText(mom));b.setOnClickListener(v->history()); }catch(Exception e){toast(e.getMessage());} }
  void privacy(){ base("Privacy & storage"); Button b=btn("← Home");root.addView(b,0);b.setOnClickListener(v->home()); note("TANU core has NO Android INTERNET permission.\n\nAudio: compressed and stored locally.\nTranscription: bundled multilingual model on device.\nMOM: generated locally in English.\nGuests: no TANU ID required.\nSharing: only after you tap Share; Android hands the MOM to the app you choose.\n\nDefault space saver: approve MOM → original audio is deleted. The compact speech model is about 31 MB."); Button clean=btn("Delete all processed audio");root.addView(clean);clean.setOnClickListener(v->{File d=new File(getFilesDir(),"audio");File[] a=d.listFiles();int n=0;if(a!=null)for(File x:a)if(x.delete())n++;toast("Deleted "+n+" audio files");}); }
  void shareText(String text){ Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"TANU Minutes of Meeting");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Share TANU MOM")); }
  String read(File f)throws Exception{ ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);}return o.toString("UTF-8"); }
  String hash(String s){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){return "";}}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)toast("Microphone allowed. Tap Start recording again.");}
}
EOF
