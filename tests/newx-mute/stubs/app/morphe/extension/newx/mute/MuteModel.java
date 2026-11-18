package app.morphe.extension.newx.mute;
/** Fixtures for the patch-generated model bridges; no Android or obfuscated X classes. */
public final class MuteModel {
    public static final class Post {
        public final long id, parent;
        public final String text, author;
        public Post quote, repost;
        public String note;
        public Post(long id, long parent, String text, String author) {
            this.id = id; this.parent = parent; this.text = text; this.author = author;
        }
    }
    public static Object postResult(Object v) { return v; }
    public static Object canonical(Object v) { return v; }
    public static Object id(Object v) { return ((Post) v).id; }
    public static Object parentId(Object v) { return ((Post) v).parent; }
    public static Object text(Object v) { return ((Post) v).text; }
    public static Object author(Object v) { return ((Post) v).author; }
    public static Object screenName(Object v) { return v; }
    public static Object quoted(Object v) { return ((Post) v).quote; }
    public static Object reposted(Object v) { return ((Post) v).repost; }
    public static Object note(Object v) { return ((Post) v).note; }
    public static Object noteResult(Object v) { return v; }
    public static Object noteText(Object v) { return v; }
}
