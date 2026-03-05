package org.sonnayasomnambula.nearby.exchanger.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.sonnayasomnambula.nearby.exchanger.R

class Toaster {
    companion object {
        fun show(text: String, context: Context, duration: Int = Toast.LENGTH_LONG) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(context, text, duration).show()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, text, duration).show()
                }
            }
        }

        fun show(throwable: Throwable, context: Context, duration: Int = Toast.LENGTH_LONG) {
            val text = throwable.message?.let { message ->
                context.getString(R.string.error_message, message)
            } ?: context.getString(R.string.critical_error)
            show(text, context, duration)
        }
    }
}