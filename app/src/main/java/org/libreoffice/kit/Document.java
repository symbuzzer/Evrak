package org.libreoffice.kit;

import android.util.Log;
import java.nio.ByteBuffer;
import androidx.annotation.Keep;

@Keep
public class Document {
    private static final String TAG = "LibreOfficeKit_Doc";
    private ByteBuffer handle;

    public Document(ByteBuffer handle) {
        this.handle = handle;
        try {
            bindMessageCallback();
        } catch (Throwable e) {
            Log.w(TAG, "bindMessageCallback failed: " + e.getMessage());
        }
    }

    @Keep
    public void messageRetrieved(int signalNumber, String payload) {
        Log.d(TAG, "Message retrieved (int): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrieved(long signalNumber, String payload) {
        Log.d(TAG, "Message retrieved (long): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrievedLOKit(int signalNumber, String payload) {
        Log.d(TAG, "Message retrieved LOKit (int): " + signalNumber + " payload: " + payload);
    }

    @Keep
    public void messageRetrievedLOKit(long signalNumber, String payload) {
        Log.d(TAG, "Message retrieved LOKit (long): " + signalNumber + " payload: " + payload);
    }

    private native void bindMessageCallback();
    
    public native void destroy();

    public int getPart() {
        try { return getPartNative(); } catch (Throwable e) { return 0; }
    }
    private native int getPartNative();

    public void setPart(int partIndex) {
        try { setPartNative(partIndex); } catch (Throwable e) {}
    }
    private native void setPartNative(int partIndex);

    public int getParts() {
        try { return getPartsNative(); } catch (Throwable e) { return 0; }
    }
    private native int getPartsNative();

    public String getPartName(int partIndex) {
        try { return getPartNameNative(partIndex); } catch (Throwable e) { return ""; }
    }
    private native String getPartNameNative(int partIndex);

    public native void saveAs(String url, String format, String options);

    public native String getError();
    public native long getDocumentHeight();
    public native long getDocumentWidth();
    public native void initializeForRendering();
}
