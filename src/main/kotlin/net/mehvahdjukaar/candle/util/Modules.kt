package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module

val Module.isCommon: Boolean
    get() = ModuleRoleDetector.isCommonModule(this)
