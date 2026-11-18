package app.morphe.extension.newx.mute;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia;

/** Keeps X's input MIME types and result parser while routing to DocumentsUI/gallery apps. */
public final class NativeMediaPicker extends PickMultipleVisualMedia {
    private final int maxItems;
    public NativeMediaPicker(int maxItems) {
        super(maxItems);
        this.maxItems = Math.min(4, Math.max(1, maxItems));
    }

    @Override public Object parseResult(int resultCode, Intent intent) {
        Object result = super.parseResult(resultCode, intent);
        if (result instanceof java.util.List<?> items && items.size() > maxItems) {
            return new java.util.ArrayList<>(items.subList(0, maxItems));
        }
        return result;
    }

    @Override public Intent createIntent(Context context, Object request) {
        Intent original = super.createIntent(context, request);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        String type = original.getType();
        intent.setType(type == null ? "*/*" : type);
        String[] types = original.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        if (types != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        else if (type == null) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        try {
            ComponentName documents = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .resolveActivity(context.getPackageManager());
            if (documents != null) intent.setComponent(documents);
        } catch (RuntimeException ignored) { }
        return intent;
    }
}
