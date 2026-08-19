package com.tanu.personal.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object PdfExporter{
    fun create(context:Context,title:String,text:String):android.net.Uri{
        val pdf=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(15,23,42);textSize=12f};val head=Paint(paint).apply{textSize=22f;isFakeBoldText=true}
        val lines=text.lines().flatMap{line->if(line.length<82)listOf(line)else line.chunked(82)};var pageNo=1;var idx=0
        while(idx<lines.size){val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo++).create());val c=page.canvas;c.drawText(title,40f,55f,head);var y=86f;while(idx<lines.size&&y<810){c.drawText(lines[idx++],40f,y,paint);y+=17f};pdf.finishPage(page)}
        val f=File(context.cacheDir,"TANU_${System.currentTimeMillis()}.pdf");f.outputStream().use{pdf.writeTo(it)};pdf.close();return FileProvider.getUriForFile(context,"${context.packageName}.files",f)
    }
}
