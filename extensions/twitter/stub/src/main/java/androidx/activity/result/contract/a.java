/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package androidx.activity.result.contract;

import android.content.Context;
import android.content.Intent;

/**
 * Compile-only stub for ActivityResultContract as obfuscated in X 12.19.1-release.0.
 */
public abstract class a<I, O> {
    public abstract Intent createIntent(Context context, I input);

    public abstract O parseResult(int resultCode, Intent intent);
}
