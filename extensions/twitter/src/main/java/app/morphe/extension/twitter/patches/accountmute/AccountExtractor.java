/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.accountmute;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Verified against X 12.19.1: k1.i is username, d.I1 is repost metadata. */
public final class AccountExtractor {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private AccountExtractor() { }

    public static Set<String> fromJsonTweet(Object tweet) {
        Set<String> handles = new LinkedHashSet<>();
        if (tweet == null) return handles;
        user(field(tweet, "W"), handles);
        Object core = field(tweet, "R");
        Object result = field(field(core, "b"), "a");
        if (result == null) result = field(core, "a");
        user(staticCall("com.twitter.model.core.entity.t1", "b", result), handles);
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        model(field(tweet, "X"), handles, visited, 0);
        model(unwrap(field(tweet, "S")), handles, visited, 0);
        model(call(field(tweet, "T"), "k"), handles, visited, 0);
        // Building preserves author + reposter metadata for both REST and GraphQL payloads.
        Object builder = returning(tweet, "com.twitter.model.core.b$a");
        model(call(builder, "k"), handles, visited, 0);
        return handles;
    }

    private static void user(Object user, Set<String> handles) {
        if (user != null && user.getClass().getName().equals("com.twitter.model.core.entity.k1")) {
            add(field(user, "i"), handles);
        }
    }
    private static void add(Object name, Set<String> handles) {
        if (name instanceof String) {
            try { handles.add(AccountTargets.normalize((String) name)); }
            catch (IllegalArgumentException ignored) { }
        }
    }
    private static void model(Object tweet, Set<String> handles, Set<Object> visited, int depth) {
        if (tweet == null || depth > 8 || !visited.add(tweet)
                || !tweet.getClass().getName().equals("com.twitter.model.core.b")) return;
        user(field(tweet, "b"), handles);
        add(field(field(field(tweet, "f"), "I1"), "c"), handles);
        model(unwrap(field(tweet, "d")), handles, visited, depth + 1);
    }
    private static Object unwrap(Object result) {
        if (result == null) return null;
        switch (result.getClass().getName()) {
            case "com.twitter.model.core.b": return result;
            case "com.twitter.model.core.l0": return field(result, "a");
            case "com.twitter.model.core.h0": return call(field(result, "d"), "getValue");
            default: return staticCall("com.twitter.model.core.i0", "c", result);
        }
    }
    private static Object field(Object object, String name) {
        if (object == null) return null;
        String key = object.getClass().getName() + '#' + name;
        try {
            Field found = FIELDS.get(key);
            if (found == null) {
                for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                    try { found = type.getDeclaredField(name); break; } catch (NoSuchFieldException ignored) { }
                }
                if (found == null) return null;
                found.setAccessible(true); FIELDS.put(key, found);
            }
            return found.get(object);
        } catch (Exception ignored) { return null; }
    }
    private static Object call(Object object, String name) {
        if (object == null) return null;
        String key = object.getClass().getName() + "#method:" + name;
        try {
            Method found = METHODS.get(key);
            if (found == null) {
                for (Class<?> type = object.getClass(); type != null && found == null; type = type.getSuperclass()) {
                    try { found = type.getDeclaredMethod(name); } catch (NoSuchMethodException ignored) { }
                }
                if (found == null) return null;
                found.setAccessible(true); METHODS.put(key, found);
            }
            return found.invoke(object);
        } catch (Exception ignored) { return null; }
    }
    private static Object returning(Object object, String returnName) {
        if (object == null) return null;
        try {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getParameterTypes().length == 0 && method.getReturnType().getName().equals(returnName)) {
                        method.setAccessible(true); return method.invoke(object);
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
    private static Object staticCall(String className, String name, Object argument) {
        if (argument == null) return null;
        try {
            for (Method method : Class.forName(className).getDeclaredMethods()) {
                if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())
                        && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isInstance(argument)) {
                    method.setAccessible(true); return method.invoke(null, argument);
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
