package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/** Expand only after applying and validating the custom hooks against another target APKM. */
internal val CUSTOM_NEW_X_COMPATIBILITY = Compatibility(
    name = "NewX",
    packageName = "com.twitter.android",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x000000,
    targets = listOf(AppTarget(version = "12.43.0-prod.01")),
)
