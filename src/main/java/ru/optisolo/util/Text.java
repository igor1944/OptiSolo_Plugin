package ru.optisolo.util;

/** Проверка имён точек телепорта и прочие мелочи. */
public final class Text {

    private Text() {
    }

    public static boolean validHomeName(String name) {
        if (name == null) return false;
        if (name.length() < 1 || name.length() > 16) return false;
        return name.matches("[A-Za-z0-9_\\-а-яА-ЯёЁ]+");
    }

    public static boolean validWorldType(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return t.equals("NORMAL") || t.equals("FLAT") || t.equals("VOID");
    }
}
