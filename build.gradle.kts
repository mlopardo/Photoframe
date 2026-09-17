// Plugins a nivel raíz. Cada módulo aplica lo que necesita.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
