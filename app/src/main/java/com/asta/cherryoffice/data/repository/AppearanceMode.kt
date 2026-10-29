package com.asta.cherryoffice.data.repository

enum class AppearanceMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED");
    companion object {
        fun fromStored(value: String?): AppearanceMode = when (value) {
            "DARK_GLOSSY", "DARK" -> DARK
            "LIGHT_CHERRY", "LIGHT" -> LIGHT
            "AMOLED_BLACK", "AMOLED" -> AMOLED
            else -> SYSTEM
        }
    }
}
