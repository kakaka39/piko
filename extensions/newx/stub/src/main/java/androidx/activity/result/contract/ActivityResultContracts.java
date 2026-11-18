package androidx.activity.result.contract;

import android.content.Context;
import android.content.Intent;

/** Compile-only signature; the real class is supplied by X. */
public final class ActivityResultContracts {
    public static class PickMultipleVisualMedia {
        public PickMultipleVisualMedia(int maxItems) {}
        public Intent createIntent(Context context, Object request) { return null; }
        public Object parseResult(int code, Intent intent) { return null; }
    }
}
