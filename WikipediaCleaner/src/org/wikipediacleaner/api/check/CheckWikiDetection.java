/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2014  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */


package org.wikipediacleaner.api.check;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;


/**
 * Bean for holding information about problems detected by Check Wiki.
 */
public class CheckWikiDetection {

  private final static Logger log = LoggerFactory.getLogger(CheckWikiDetection.class);

  /** Full line. */
  private final String line;

  /** Method to interpret the location. */
  private final char locationMethod;

  /** Error number (algorithm). */
  private final int errorNumber;

  /** Location of the error in the wiki text. */
  private final int location;

  /** Wiki text where the error was found. */
  private final String detection;

  /**
   * Analyze a line produced by checkarticle.cgi
   * 
   * @param line Line.
   * @param page Page.
   * @return Detection.
   */
  public static CheckWikiDetection analyzeLine(String line, Page page) {
    if (line == null) {
      return null;
    }
    line = line.trim();
    if ((line.isEmpty()) || "None".equalsIgnoreCase(line)) {
      return null;
    }
    char locationMethod = '-';
    String originalLine = line;
    if ((line.length() > 1) &&
        !Character.isDigit(line.charAt(0)) &&
        (line.charAt(1) == ' ')) {
      locationMethod = line.charAt(0);
      line = line.substring(2);
    }
    int spaceIndex = line.indexOf(' ');
    if (spaceIndex <= 0) {
      return null;
    }
    final String whiteListPrefix = "%s is in whitelist with error: ".formatted(page.getTitle());
    if (line.startsWith(whiteListPrefix)) {
      try {
        int errorNumber = Integer.parseInt(line.substring(whiteListPrefix.length()));
        return new CheckWikiDetection(originalLine, locationMethod, errorNumber, -1, null);
      } catch (NumberFormatException e) {
        log.error("Line with invalid format: {}", line);
      }
    }
    try {
      int errorNumber = Integer.parseInt(line.substring(0, spaceIndex));
      line = line.substring(spaceIndex).trim();
      spaceIndex = line.indexOf(' ');
      int location;
      String detection = null;
      if (spaceIndex <= 0) {
        location = Integer.parseInt(line);
      } else {
        location = Integer.parseInt(line.substring(0, spaceIndex));
        line = line.substring(spaceIndex).trim();
        detection = line;
      }
      return new CheckWikiDetection(
          originalLine, locationMethod, errorNumber, location, detection);
    } catch (NumberFormatException e) {
      log.error("Line with invalid format: {}", line);
      return null;
    }
  }

  /**
   * @param line Full line.
   * @param locationMethod Method to interpret the location.
   * @param errorNumber Error number (algorithm).
   * @param location Location of the error.
   * @param detection Text near the error.
   */
  private CheckWikiDetection(
      String line,
      char locationMethod, int errorNumber,
      int location, String detection) {
    this.line = line;
    this.locationMethod = locationMethod;
    this.errorNumber = errorNumber;
    this.location = location;
    this.detection = detection;
  }

  /**
   * @return Full line.
   */
  public String getLine() {
    return line;
  }

  /**
   * @return Error number (algorithm).
   */
  public int getErrorNumber() {
    return errorNumber;
  }

  /**
   * @return Location method of the error.
   */
  public char getLocationMethod() {
    return locationMethod;
  }

  /**
   * @return Location of the error.
   */
  public int getLocation() {
    return location;
  }

  /**
   * @param analysis Page analysis.
   * @return Location of the error.
   */
  public int getLocation(PageAnalysis analysis) {
    if (location < 0) {
      return location;
    }
    if (locationMethod == '+') {
      int currentIndex = 0;
      int currentCount = 0;
      String contents = analysis.getContents();
      while ((currentIndex < contents.length()) &&
             (currentCount < location)) {
        boolean moved = false;
        ContentsComment comment = analysis.comments().getBeginsAt(currentIndex);
        if (comment != null) {
          moved = true;
          currentIndex = comment.getEndIndex();
        }
        // TODO: ignore other things
        if (!moved) {
          currentIndex++;
          currentCount++;
        }
      }
      return currentIndex;
    }
    return location;
  }

  /**
   * @return Text near the error.
   */
  public String getDetection() {
    return detection;
  }
}
