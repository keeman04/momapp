package com.tanu.personal.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context:Context){
    private val p=context.getSharedPreferences("tanu_settings",Context.MODE_PRIVATE)
    var userName:String get()=p.getString("user_name","You")?:"You"; set(v){p.edit().putString("user_name",v).apply()}
    var serverUrl:String get()=p.getString("server_url","")?:""; set(v){p.edit().putString("server_url",v.trim().trimEnd('/')).apply()}
    var customVocabulary:String get()=p.getString("vocab","VGP, TANU, MOM, quotation, EOD, Tamil, Tanglish, Hinglish")?:""; set(v){p.edit().putString("vocab",v).apply()}
    var retention:String get()=p.getString("retention","after_mom")?:"after_mom"; set(v){p.edit().putString("retention",v).apply()}
    var floatingEnabled:Boolean get()=p.getBoolean("floating",false); set(v){p.edit().putBoolean("floating",v).apply()}
    var defaultMode:String get()=p.getString("mode",ProcessingMode.FAST)?:ProcessingMode.FAST; set(v){p.edit().putString("mode",v).apply()}
}
