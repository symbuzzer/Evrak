package org.libreoffice.kit;

import android.util.Log;
import java.nio.ByteBuffer;
import androidx.annotation.Keep;

@Keep
public class Office {
    private static final String TAG = "LibreOfficeKit_Office";
    private ByteBuffer handle;

    public Office(ByteBuffer handle) {
        this.handle = handle;
        try {
            bindMessageCallback();
        } catch (Throwable e) {
            Log.w(TAG, "bindMessageCallback failed: " + e.getMessage());
        }
    }

    public Document documentLoad(String url) {
        try {
            ByteBuffer documentHandle = documentLoadNative(url);
            if (documentHandle != null) {
                return new Document(documentHandle);
            }
        } catch (Throwable e) {
            Log.e(TAG, "documentLoadNative failed: " + e.getMessage());
        }
        return null;
    }

    @Keep
    public void messageRetrieved(int signalNumber, String payload) {
        Log.d(TAG, "Office Message (int): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrieved(long signalNumber, String payload) {
        Log.d(TAG, "Office Message (long): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrievedLOKit(int signalNumber, String payload) {
        Log.d(TAG, "Office Message LOKit (int): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrievedLOKit(long signalNumber, String payload) {
        Log.d(TAG, "Office Message LOKit (long): " + signalNumber + " payload: " + payload);
    }

    private native void bindMessageCallback();
    public native String getError();
    private native ByteBuffer documentLoadNative(String url);
    public native void destroy();
    public native void destroyAndExit();
    public native void setDocumentPassword(String url, String pwd);
    public native void setOptionalFeatures(long options);

    public static Office get() {
        try {
            ByteBuffer handle = LibreOfficeKit.getLibreOfficeKitHandle();
            if (handle == null) return null;
            return new Office(handle);
        } catch (Throwable e) {
            Log.e(TAG, "getLibreOfficeKitHandle failed: " + e.getMessage());
            return null;
        }
    }
}
