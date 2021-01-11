/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a0xx.a00x.a008;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.data.PageElementTitle;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.ContentsUtil;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.api.data.contents.title.TitleBuilder;


/**
 * Algorithm for analyzing error 8 of check wikipedia project.
 * Error 8: Headline should end with "="
 */
public class CheckErrorAlgorithm008 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm008() {
    super("Headline should end with \"=\"");
  }

  /**
   * Analyze a page to check if errors are present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param onlyAutomatic True if analysis could be restricted to errors automatically fixed.
   * @return Flag indicating if the error was found.
   */
  @Override
  public boolean analyze(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors, boolean onlyAutomatic) {
    if (analysis == null) {
      return false;
    }

    // Check every "=" at the beginning of a line
    boolean result = false;
    String contents = analysis.getContents();
    int currentIndex = 0;
    while (currentIndex < contents.length()) {
      result |= analyzeLine(analysis, errors, contents, currentIndex);
      currentIndex =  ContentsUtil.getLineEndIndex(contents, currentIndex) + 1;
    }

    return result;
  }

  /**
   * Analyze a line of text to see if there's an error.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param contents Page contents.
   * @param lineBeginIndex Index of the beginning of the line.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeLine(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      String contents,
      int lineBeginIndex) {

    // Check that the line begins with a "="
    if (contents.charAt(lineBeginIndex) != '=') {
      return false;
    }

    // Check that's indeed an error
    if (analysis.comments().isAt(lineBeginIndex)) {
      return false;
    }
    PageElementTitle title = analysis.isInTitle(lineBeginIndex);
    if ((title != null) && (title.getSecondLevel() >= title.getFirstLevel())) {
      String after = title.getAfterTitle();
      if ((after == null) || (after.trim().isEmpty())) {
        return false;
      }
      int afterTitleIndex = ContentsUtil.moveIndexAfterWhitespace(contents, title.getAfterTitleIndex());
      int beginIndex = title.getBeginIndex();
      int endIndex = title.getEndIndex();
      if ((afterTitleIndex < endIndex) && (contents.charAt(afterTitleIndex) == '<')) {
        ContentsComment comment = analysis.comments().getAt(afterTitleIndex);
        if ((comment != null) && (comment.getEndIndex() >= endIndex)) {
          return false;
        }
      }
      CheckErrorResult errorResult = createCheckErrorResult(
          analysis, beginIndex, endIndex);
      String replacement =
          contents.substring(beginIndex, afterTitleIndex) +
          "\n" +
          contents.substring(afterTitleIndex, endIndex);
      errorResult.addReplacement(replacement);
      replacement =
          contents.substring(beginIndex, afterTitleIndex - title.getSecondLevel()) +
          contents.substring(afterTitleIndex, endIndex) +
          contents.substring(afterTitleIndex - title.getSecondLevel(), afterTitleIndex);
      errorResult.addReplacement(replacement);
      errors.add(errorResult);
      return true;
    }
    if ((analysis.getSurroundingTag(HtmlTagType.CODE, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.MATH, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.MATH_CHEM, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.NOWIKI, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.PRE, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.SCORE, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.SOURCE, lineBeginIndex) != null) ||
        (analysis.getSurroundingTag(WikiTagType.SYNTAXHIGHLIGHT, lineBeginIndex) != null)) {
      return false;
    }

    // Report the error
    if (errors == null) {
      return true;
    }

    // Find end of a line and potential "=" sign
    int equalIndex = -1;
    int endLineIndex = lineBeginIndex;
    int equalsCount = 0;
    while ((endLineIndex < contents.length()) &&
        (contents.charAt(endLineIndex) == '=')) {
      endLineIndex++;
      equalsCount++;
    }
    while ((endLineIndex < contents.length()) &&
        (contents.charAt(endLineIndex) != '\n')) {
      if ((equalIndex < 0) &&
          (contents.charAt(endLineIndex) == '=')) {
        equalIndex = endLineIndex;
      }
      endLineIndex++;
    }

    // Create error
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, lineBeginIndex, endLineIndex);
    if (endLineIndex > lineBeginIndex + equalsCount) {
      errorResult.addReplacement(TitleBuilder.from(
          equalsCount,
          contents.substring(lineBeginIndex + equalsCount, endLineIndex)).toString());
    } else {
      errorResult.addReplacement(StringUtils.EMPTY);
    }
    if (equalIndex > 0) {
      String firstPart = contents.substring(lineBeginIndex + equalsCount, equalIndex); 
      errorResult.addReplacement(TitleBuilder.from(
          equalsCount, firstPart).toString());
      int secondEqualsCount = 0;
      while ((equalIndex < endLineIndex) && (contents.charAt(equalIndex) == '=')) {
        equalIndex++;
        secondEqualsCount++;
      }
      errorResult.addReplacement(TitleBuilder.from(
          secondEqualsCount, firstPart).toString());
      if (equalIndex < endLineIndex) {
        errorResult.addReplacement(
            TitleBuilder.from(equalsCount, firstPart).toString() +
            "\n" +
            contents.substring(equalIndex, endLineIndex));
      }
    }
    errors.add(errorResult);
    return true;
  }
}
