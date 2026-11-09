package org.sensorhub.android.ui.features.settings.help

sealed class FaqItem {
    data class Header(val title: String) : FaqItem()
    data class Entry(val question: String, val answer: String) : FaqItem()
}