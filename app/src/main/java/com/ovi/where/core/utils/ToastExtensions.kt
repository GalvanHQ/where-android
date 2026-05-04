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

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.showLongToast(uiText: UiText) {
    Toast.makeText(this, uiText.asString(this), Toast.LENGTH_LONG).show()
}
