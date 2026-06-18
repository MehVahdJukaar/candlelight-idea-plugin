package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module

/** True when this Gradle module is the cross-platform common source set. */
val Module.isCommon: Boolean
    get() = ModuleRoleDetector.isCommonModule(this)
