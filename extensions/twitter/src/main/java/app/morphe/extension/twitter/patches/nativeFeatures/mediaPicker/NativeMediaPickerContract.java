/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.mediaPicker;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Opens DocumentsUI and returns its image Uris to X's existing composer callback. */
public final class NativeMediaPickerContract extends a<Object, List<Uri>> {
    private static final int COMPOSER_MAX_MEDIA_ITEMS = 4;

    private final int maxItems;

    public NativeMediaPickerContract(int maxItems) {
        this.maxItems = Math.min(Math.max(maxItems, 1), COMPOSER_MAX_MEDIA_ITEMS);
    }

    @Override
    public Intent createIntent(Context context, Object input) {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
