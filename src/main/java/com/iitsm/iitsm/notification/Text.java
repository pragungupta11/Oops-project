package com.iitsm.iitsm.notification;

final class Text {
    private Text() { }

    static String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
