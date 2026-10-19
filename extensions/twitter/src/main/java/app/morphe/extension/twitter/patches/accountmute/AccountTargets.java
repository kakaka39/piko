/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.accountmute;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Handles are identifiers, not display names or keyword substrings. */
public final class AccountTargets {
    private AccountTargets() { }
    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("Empty account");
        String handle = input.trim();
        if (handle.startsWith("@")) handle = handle.substring(1);
        if (!handle.matches("[A-Za-z0-9_]{1,15}")) throw new IllegalArgumentException(input);
        return handle.toLowerCase(Locale.ROOT);
    }
    public static final class Batch {
        public final List<String> handles = new ArrayList<>();
        public final List<String> invalid = new ArrayList<>();
        public int duplicates;
    }
    public static Batch parse(String input) {
        Batch batch = new Batch();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : input.split("[\\s\\p{Z},、;；]+")) {
            if (token.isEmpty()) continue;
            try {
                if (!unique.add(normalize(token))) batch.duplicates++;
            } catch (IllegalArgumentException e) { batch.invalid.add(token); }
        }
        batch.handles.addAll(unique);
        return batch;
    }
}
