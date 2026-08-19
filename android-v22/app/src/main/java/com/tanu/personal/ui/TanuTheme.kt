package com.tanu.personal.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TanuBlue=Color(0xFF2563EB);val TanuPurple=Color(0xFF7C3AED);val TanuPink=Color(0xFFEC4899);val TanuOrange=Color(0xFFFB923C);val TanuCyan=Color(0xFF06B6D4);val TanuInk=Color(0xFF0F172A)
private val scheme=lightColorScheme(primary=TanuBlue,secondary=TanuPurple,tertiary=TanuPink,background=Color(0xFFF8FAFF),surface=Color.White,onBackground=TanuInk,onSurface=TanuInk,error=Color(0xFFEF4444))
@Composable fun TanuTheme(content: @Composable () -> Unit){MaterialTheme(colorScheme=scheme,typography=Typography(),content=content)}
