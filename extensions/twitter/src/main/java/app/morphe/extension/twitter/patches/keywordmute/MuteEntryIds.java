/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.keywordmute;

import java.util.function.LongPredicate;

public final class MuteEntryIds {
    private MuteEntryIds() { }
    public static boolean any(String entryId, LongPredicate muted) {
        if (entryId == null) return false;
        long value = 0;
        boolean digits = false, overflow = false;
        for (int i = 0; i <= entryId.length(); i++) {
            char c = i < entryId.length() ? entryId.charAt(i) : '\0';
            if (c >= '0' && c <= '9') {
                digits = true;
                if (value > (Long.MAX_VALUE - (c - '0')) / 10) overflow = true;
                if (!overflow) value = value * 10 + c - '0';
            } else {
                if (digits && !overflow && value > 0 && muted.test(value)) return true;
                digits = overflow = false; value = 0;
            }
        }
        return false;
    }
}
