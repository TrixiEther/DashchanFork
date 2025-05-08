package com.mishiranu.dashchan.util;

public class FilenameUtils {

    private static final String FILENAME_BLOCKED_CHARACTERS = "\\/:*?\"<>|.";
    private static final int FILENAME_MAX_CHARACTER_COUNT = 255;

    public static boolean isValidCharacter(char ch) {
        return !FILENAME_BLOCKED_CHARACTERS.contains(Character.toString(ch));
    }

    public static String getFilenameBlockedCharacters() {
        return FILENAME_BLOCKED_CHARACTERS;
    }

    public static int getFilenameMaxCharacterCount() {
        return FILENAME_MAX_CHARACTER_COUNT;
    }

}
