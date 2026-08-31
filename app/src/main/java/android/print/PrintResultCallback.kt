package android.print

import android.os.ParcelFileDescriptor

object PrintResultCallback {

    abstract class Layout : PrintDocumentAdapter.LayoutResultCallback()
    
    abstract class Write : PrintDocumentAdapter.WriteResultCallback()

    fun createLayoutCallback(
        onSuccess: (info: PrintDocumentInfo?, changed: Boolean) -> Unit,
        onFailure: (error: CharSequence?) -> Unit
    ): PrintDocumentAdapter.LayoutResultCallback {
        return object : Layout() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                onSuccess(info, changed)
            }
            override fun onLayoutFailed(error: CharSequence?) {
                onFailure(error)
            }
        }
    }

    fun createWriteCallback(
        onSuccess: (pages: Array<out PageRange>?) -> Unit,
        onFailure: (error: CharSequence?) -> Unit
    ): PrintDocumentAdapter.WriteResultCallback {
        return object : Write() {
            override fun onWriteFinished(pages: Array<out PageRange>?) {
                onSuccess(pages)
            }
            override fun onWriteFailed(error: CharSequence?) {
                onFailure(error)
            }
        }
    }
}
