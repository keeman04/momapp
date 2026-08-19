package com.tanu.personal.service

import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.*
import com.tanu.personal.MainActivity
import com.tanu.personal.R

class FloatingAssistantService:Service(){
    private var wm:WindowManager?=null;private var bubble:View?=null
    override fun onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){stopSelf();return};showBubble()}
    private fun showBubble(){wm=getSystemService(WINDOW_SERVICE) as WindowManager;val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=android.graphics.drawable.GradientDrawable().apply{setColor(0xFFFFFFFF.toInt());cornerRadius=36f};elevation=14f;setPadding(12,12,12,12)}
        val icon=ImageView(this).apply{setImageResource(R.drawable.tanu_app_icon);scaleType=ImageView.ScaleType.CENTER_CROP};box.addView(icon,LinearLayout.LayoutParams(112,112));
        val p=WindowManager.LayoutParams(136,136,if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END;x=20;y=260}
        var downX=0f;var downY=0f;var startX=0;var startY=0
        box.setOnTouchListener{v,e->when(e.action){MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;startX=p.x;startY=p.y;true};MotionEvent.ACTION_MOVE->{p.x=(startX-(e.rawX-downX)).toInt();p.y=(startY+(e.rawY-downY)).toInt();wm?.updateViewLayout(box,p);true};MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.rawX-downX)<18&&kotlin.math.abs(e.rawY-downY)<18)openApp();true};else->false}}
        bubble=box;wm?.addView(box,p)
    }
    private fun openApp(){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_new_meeting",true))}
    override fun onDestroy(){bubble?.let{runCatching{wm?.removeView(it)}};bubble=null;super.onDestroy()}
    override fun onBind(intent:Intent?)=null
}
