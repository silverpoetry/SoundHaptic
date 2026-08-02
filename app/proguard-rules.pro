# Keep the Modern Xposed entry named in META-INF/xposed/java_init.list stable if shrinking is
# enabled for a future release. Current releases remain unobfuscated for diagnosable stack traces.
-keep public class com.weicheng.soundhaptic.SoundHapticModule { *; }
