package com.example.taskboard.task;

import java.util.regex.Pattern;

/**
 * A title or description the way the host keeps it in TSK-TITLE or TSK-DESC: on one line, because a
 * host record is one line, so control characters such as line breaks become spaces; without leading
 * or trailing spaces, because the host pads its fields with spaces; and null when blank, because a
 * blank field on the host is no text. The datasync loads the host's texts the same way, so a task
 * reads the same before and after its round trip to the host.
 */
final class HostText {

  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cc}");

  private HostText() {}

  /** The text as the host keeps it, or null if it is null or blank. */
  static String of(String text) {
    if (text == null) {
      return null;
    }
    String line = CONTROL_CHARACTERS.matcher(text).replaceAll(" ").strip();
    return line.isEmpty() ? null : line;
  }
}
