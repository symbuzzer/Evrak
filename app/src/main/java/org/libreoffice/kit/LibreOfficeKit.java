package org.libreoffice.kit;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.nio.ByteBuffer;

public final class LibreOfficeKit {
    private static final String TAG = "LibreOfficeKit";
    private static boolean initializeDone = false;

    static {
        loadNativeLibraries();
    }

    private static void loadNativeLibraries() {
        String[] libs = {
            "nss3", "ssl3", "nspr4", "plc4", "plds4", "sqlite3", "freebl3", "lo-native-code"
        };
        
        for (String lib : libs) {
            try {
                System.loadLibrary(lib);
                Log.d(TAG, "Native library 'lib" + lib + ".so' loaded.");
            } catch (Throwable e) {
                if (lib.equals("lo-native-code")) {
                    Log.e(TAG, "CRITICAL: Main native library 'liblo-native-code.so' could not be loaded: " + e.getMessage());
                } else {
                    Log.w(TAG, "Optional/Dependency library 'lib" + lib + ".so' not loaded: " + e.getMessage());
                }
            }
        }
    }

    private static native boolean initializeNative(String dataDir, String cacheDir, String apkFile, AssetManager mgr);
    
    public static native ByteBuffer getLibreOfficeKitHandle();
    
    public static native void putenv(String string);
    
    public static native void redirectStdio(boolean state);

    public static synchronized boolean init(Activity activity) {
        if (initializeDone) {
            return true;
        }
        
        try {
            AssetManager mgr = activity.getResources().getAssets();
            String dataDir = activity.getFilesDir().getAbsolutePath(); 
            File safeCacheDir = new File(activity.getFilesDir(), "lok_cache");
            if (!safeCacheDir.exists()) safeCacheDir.mkdirs();
            String cacheDir = safeCacheDir.getAbsolutePath();
            String apkFile = activity.getPackageResourcePath();

            Log.d(TAG, "Initializing LibreOfficeKit with: dataDir=" + dataDir + ", cacheDir=" + cacheDir);

            try {
                redirectStdio(true);
            } catch (Throwable t) {
                Log.w(TAG, "redirectStdio not available: " + t.getMessage());
            }

            if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
                Log.e(TAG, "initializeNative returned false!");
                return false;
            }
            initializeDone = true;
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "LibreOfficeKit init FAILED: " + e.toString());
            return false;
        }
    }
}
