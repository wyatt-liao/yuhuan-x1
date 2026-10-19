package com.yuhuan.x1.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal=Color(0xFF004A45)
val TealMid=Color(0xFF12635D)
val Mint=Color(0xFFD4E6E5)
val Paper=Color(0xFFF6FAFA)
val Ink=Color(0xFF181C1C)
val Slate=Color(0xFF516161)
val Line=Color(0xFFE5E9E8)
val Quiet=Color(0xFFF0F4F4)
val Red=Color(0xFFBA1A1A)
@Composable fun X1Theme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,primaryContainer=Mint,onPrimaryContainer=Teal,
        secondary=Slate,secondaryContainer=Mint,background=Paper,surface=Color.White,onSurface=Ink,onSurfaceVariant=Slate,
        outlineVariant=Line,error=Red,surfaceContainer=Quiet),
        typography=Typography(
            headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=28.sp,lineHeight=36.sp),
            headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=24.sp,lineHeight=32.sp),
            titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=28.sp),
            titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=24.sp),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp)),content=content)
}
