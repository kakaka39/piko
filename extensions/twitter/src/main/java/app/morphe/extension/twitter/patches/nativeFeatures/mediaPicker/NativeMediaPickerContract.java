/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.mediaPicker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.Utils;

/** Opens DocumentsUI with gallery app support and returns image Uris to X's composer callback. */
public final class NativeMediaPickerContract extends a<Object, List<Uri>> {
    private static final int COMPOSER_MAX_MEDIA_ITEMS = 4;

    private final int maxItems;

    public NativeMediaPickerContract(int maxItems) {
        this.maxItems = Math.min(Math.max(maxItems, 1), COMPOSER_MAX_MEDIA_ITEMS);
    }

    @Override
    public Intent createIntent(Context context, Object input) {
        // GET_CONTENT also allows gallery apps that do not expose a DocumentsProvider.
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return prepareIntent(context, intent);
    }

    /** Configures the existing inline-reply Intent without changing its selection options. */
    public static Intent prepareIntent(Intent intent) {
        return prepareIntent(Utils.getContext(), intent);
    }

    private static Intent prepareIntent(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (context == null) {
            return intent;
        }

        try {
            // Resolve DocumentsUI the same way Photo Picker's "Browse" action does.
            // Keep GET_CONTENT on the outgoing Intent so gallery apps remain available.
            ComponentName documentsUi = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .resolveActivity(context.getPackageManager());
            if (documentsUi != null) {
                intent.setComponent(documentsUi);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the system's normal GET_CONTENT handler if resolution fails.
        }
        return intent;
    }

    @Override
    public List<Uri> parseResult(int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return Collections.emptyList();
        }

        Set<Uri> uris = new LinkedHashSet<>();
        addUri(uris, intent.getData());

        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount() && uris.size() < maxItems; index++) {
                addUri(uris, clipData.getItemAt(index).getUri());
            }
        }

        if (uris.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(uris);
    }

    private void addUri(Set<Uri> uris, Uri uri) {
        if (uri != null && uris.size() < maxItems) {
            uris.add(uri);
        }
    }
}
