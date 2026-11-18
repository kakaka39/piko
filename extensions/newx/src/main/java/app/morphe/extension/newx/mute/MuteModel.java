package app.morphe.extension.newx.mute;

/** Bodies are generated from semantic model fields while patching the target APKM. */
public final class MuteModel {
    private MuteModel() {}
    public static Object postResult(Object value) { return null; }
    public static Object contextualCanonical(Object value) { return null; }
    public static Object repostedCanonical(Object value) { return null; }
    public static Object reposted(Object value) { return null; }
    public static Object contextualQuoted(Object value) { return null; }
    public static Object repostedQuoted(Object value) { return null; }
    public static Object note(Object value) { return null; }
    public static Object noteResult(Object value) { return null; }
    public static Object noteText(Object value) { return null; }
    public static Object id(Object value) { return null; }
    public static Object parentId(Object value) { return null; }
    public static Object text(Object value) { return null; }
    public static Object author(Object value) { return null; }
    public static Object screenName(Object value) { return null; }
    public static Object canonical(Object value) {
        Object post = contextualCanonical(value);
        return post != null ? post : repostedCanonical(value);
    }
    public static Object quoted(Object value) {
        Object quoted = contextualQuoted(value);
        return quoted != null ? quoted : repostedQuoted(value);
    }
}
