package com.twitter.model.core;
public class h0 {
    public Lazy d;
    public h0(b tweet) { d = new Lazy(tweet); }
    public static class Lazy {
        private final b tweet;
        public Lazy(b tweet) { this.tweet = tweet; }
        public Object getValue() { return tweet; }
    }
}
