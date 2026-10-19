package com.twitter.model.core;
public class b {
    public Object b, f, d;
    public static class a {
        private final b model;
        public a(b model) { this.model = model; }
        public Object k() { return model; } // Actual builder has an erased Object return.
    }
}
