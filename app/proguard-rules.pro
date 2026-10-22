# Shizuku UserService dibuat lewat reflection oleh proses Shizuku
-keep class com.siroha.relifemotion.priv.PrivilegedService { *; }
-keep class com.siroha.relifemotion.IPrivilegedService { *; }
-keep class com.siroha.relifemotion.IPrivilegedService$Stub { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.**
