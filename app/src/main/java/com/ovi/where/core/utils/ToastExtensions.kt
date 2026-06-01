package com.ovi.where.core.utils

import android.content.Context
import android.widget.Toast
import com.ovi.where.core.common.UiText

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(uiText: UiText, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, uiText.asString(this), duration).show()
}

fun UiText.resolve(context: Context? = null): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.StringResource -> context?.getString(resId, *args) ?: ""
    }
}
