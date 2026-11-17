package com.example.project_flow.aiteam;

import java.util.regex.Pattern;

/** Finds "@CFO" in a comment. Whole word only, so "@CFOs" or "email@cfo.example" do not match. */
public final class AiMentions {

    private static final Pattern CFO = Pattern.compile("(?<![A-Za-z0-9_.@])@cfo(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);

    private AiMentions() {
    }

    public static boolean mentionsCfo(String text) {
        return text != null && CFO.matcher(text).find();
    }
}
