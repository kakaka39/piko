package app.crimera.patches.utils

import app.crimera.patches.common.ShapeFingerprint as SharedShapeFingerprint
import app.crimera.patches.common.scopedMatchAll as sharedScopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull as sharedScopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match

// Legacy Twitter callers keep their source contract while NewX uses the shared API directly.
internal typealias ShapeFingerprint = SharedShapeFingerprint

internal fun Fingerprint.scopedMatchAllOrNull(): List<Match>? =
    this.sharedScopedMatchAllOrNull()

internal fun Fingerprint.scopedMatchAll(): List<Match> =
    this.sharedScopedMatchAll()
