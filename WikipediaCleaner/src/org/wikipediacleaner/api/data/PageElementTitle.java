/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.data;

import java.util.List;

import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.contents.comment.ContainerComment;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.api.data.contents.title.TitleBuilder;


/**
 * Class containing information about a title (== Title ==). 
 */
public class PageElementTitle extends PageElement {

  private final int firstLevel;
  private final int secondLevel;
  private final String titleNotTrimmed;
  private final String title;
  private final String afterTitleNotTrimmed;
  private final int afterTitleIndex;
  private final boolean multiline;

  /**
   * Analyze contents to check if it matches a title.
   * 
   * @param wikipedia Wikipedia.
   * @param contents Contents.
   * @param index Block start index.
   * @param comments Comments in the page.
   * @param tags Tags in the page.
   * @return Block details it there's a block.
   */
  public static PageElementTitle analyzeBlock(
      EnumWikipedia wikipedia, String contents, int index,
      ContainerComment comments,
      List<PageElementTag> tags) {
    // Verify arguments
    if (contents == null) {
      return null;
    }
    int maxLength = contents.length();

    // Check that this is an equal sign at the beginning of a line
    int beginIndex = index;
    boolean hasNewLine = false;
    while ((index >= 0) && !hasNewLine) {
      index--;
      if (index < 0) {
        hasNewLine = true;
      } else if (contents.charAt(index) == '\n') {
        hasNewLine = true;
      } else if (contents.charAt(index) == '>') {
        ContentsComment comment = comments.getEndsAt(index + 1);
        if (comment == null) {
          return null;
        }
        index = comment.getBeginIndex();
      } else {
        return null;
      }
    }

    // Compute first title level
    int firstLevel = 0;
    index = beginIndex;
    while ((index < maxLength) && (contents.charAt(index) == '=')) {
      index++;
      firstLevel++;
    }
    if (index >=  maxLength) {
      return null;
    }
    int beginTitleIndex = index;

    // Analyze title
    boolean endFound = false;
    boolean jump = false;
    int secondLevel = 0;
    int lastEqualIndex = index;
    int endTitleIndex = index;
    while ((index < maxLength) && (contents.charAt(index) != '\n')) {
      char currentChar = contents.charAt(index);
      int nextIndex = index + 1;
      if (Character.isWhitespace(currentChar)) {
        // Nothing to do, continue
      } else if (currentChar == '=') {
        // Equal sign, possible end of title
        if (!endFound) {
          endTitleIndex = index;
          endFound = true;
          secondLevel = 0;
        }
        secondLevel++;
        lastEqualIndex = index;
      } else if (currentChar == '<') {
        ContentsComment comment = comments.getBeginsAt(index);
        if (comment == null) {
          PageElementTag ref = null;
          for (PageElementTag tmpTag : tags) {
            if ((tmpTag.getBeginIndex() == index) &&
                WikiTagType.REF.equals(tmpTag.getType()) &&
                (tmpTag.isComplete())) {
              ref = tmpTag;
            }
          }
          if (ref == null) {
            endFound = false;
          } else {
            nextIndex = ref.getCompleteEndIndex();
            jump = true;
          }
        } else {
          nextIndex = comment.getEndIndex();
          jump = true;
        }
      } else {
        endFound = false;
      }
      index = nextIndex;
    }
    int endIndex = index;

    if (!endFound) {
      return null;
    }
    boolean multiline = false;
    if (jump) {
      for (int i = beginIndex; i < endIndex; i++) {
        if (contents.charAt(i) == '\n') {
          multiline = true;
        }
      }
    }

    // Remove extra spaces at the end of the title
    while ((endIndex > beginIndex) &&
           (" \t".indexOf(contents.charAt(endIndex - 1)) >= 0)) {
      endIndex--;
    }

    return new PageElementTitle(
        beginIndex, endIndex,
        firstLevel, secondLevel,
        contents.substring(beginTitleIndex, endTitleIndex),
        contents.substring(lastEqualIndex + 1, endIndex),
        lastEqualIndex + 1,
        multiline);
  }

  /**
   * @return Title level.
   */
  public int getLevel() {
    return Math.min(firstLevel, secondLevel);
  }

  /**
   * @return True if there's nothing questionable about this title.
   */
  public boolean isCoherent() {
    return (firstLevel == secondLevel);
  }

  /**
   * @return Number of "=" before the title.
   */
  public int getFirstLevel() {
    return firstLevel;
  }

  /**
   * @return Number of "=" after the title.
   */
  public int getSecondLevel() {
    return secondLevel;
  }

  /**
   * @return Title itself.
   */
  public String getTitle() {
    return title;
  }

  /**
   * @return Title not trimmed.
   */
  public String getTitleNotTrimmed() {
    return titleNotTrimmed;
  }

  /**
   * @return Text after title.
   */
  public String getAfterTitle() {
    return afterTitleNotTrimmed;
  }

  /**
   * @return Index of text after title.
   */
  public int getAfterTitleIndex() {
    return afterTitleIndex;
  }

  /**
   * @return True if title spans on several lines.
   */
  public boolean isMultiline() {
    return multiline;
  }

  /**
   * @param beginIndex Begin index.
   * @param endIndex End infex.
   * @param firstLevel Title level (using the first "=")
   * @param secondLevel Title level (using the last "=")
   * @param title Title itself.
   * @param afterTitle Text after the title.
   * @param multiline True if title spans on several lines.
   */
  private PageElementTitle(
      int beginIndex, int endIndex,
      int firstLevel, int secondLevel,
      String title,
      String afterTitle, int afterTitleIndex,
      boolean multiline) {
    super(beginIndex, endIndex);
    this.firstLevel = firstLevel;
    this.secondLevel = secondLevel;
    this.titleNotTrimmed = title;
    this.title = (title != null) ? title.trim() : null;
    this.afterTitleNotTrimmed = afterTitle;
    this.afterTitleIndex = afterTitleIndex;
    this.multiline = multiline;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return TitleBuilder
        .from(firstLevel, titleNotTrimmed)
        .withSecondLevel(secondLevel)
        .withTrimTitle(false)
        .withAfter(afterTitleNotTrimmed)
        .withTrimAfter(false)
        .toString();
  }
}
