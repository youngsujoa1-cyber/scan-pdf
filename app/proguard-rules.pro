# 앱 축소/난독화 시 AdMob·CameraX 가 리플렉션으로 찾는 것들을 남긴다
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**
