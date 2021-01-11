/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm;

import java.util.Collection;
import java.util.List;

import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.TagBuilder;
import org.wikipediacleaner.api.data.contents.tag.TagFormat;


/**
 * Algorithm for analyzing error 100 of check wikipedia project.
 * Error 100: List tag (&lt;ol&gt;, &lt;ul&gt; or &lt;li&gt;) with no correct match.
 */
public class CheckErrorAlgorithm100 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm100() {
    super("List tag (<ol>, <ul> or <li>) with no correct match.");
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

    // Check every tag
    List<PageElementTag> tags = analysis.getTags();
    if ((tags == null) || (tags.isEmpty())) {
      return false;
    }
    boolean result = false;
    for (PageElementTag tag : tags) {

      // Check if tag is an incomplete list tag
      boolean shouldReport = false;
      if (isListTag(tag) && !tag.isComplete()) {
        shouldReport = true;
      }

      // Special cases for <li> tags
      // @see https://html.spec.whatwg.org/multipage/semantics.html#the-li-element
      if (shouldReport && HtmlTagType.LI.equals(tag.getType())) {
        int index = tag.getBeginIndex();
        if ((analysis.getSurroundingTag(HtmlTagType.OL, index) != null) ||
            (analysis.getSurroundingTag(HtmlTagType.UL, index) != null)) {
          shouldReport = false;
        }
      }

      // Report error
      if (shouldReport) {
        if (errors == null) {
          return true;
        }
        result = true;
        int beginIndex = tag.getBeginIndex();
        int endIndex = tag.getEndIndex();
        String replacement = null;
        boolean automatic = false;

        // Manage suggestions
        if (HtmlTagType.LI.equals(tag.getType())) {
          int tmpIndex = beginIndex;
          String contents = analysis.getContents();
          while ((tmpIndex > 0) && (contents.charAt(tmpIndex - 1) == ' ')) {
            tmpIndex--;
          }
          if ((tmpIndex == 0) || (contents.charAt(tmpIndex - 1) == '\n')) {
            tmpIndex = endIndex;
            boolean shouldContinue = true;
            while (shouldContinue && (tmpIndex < contents.length())) {
              char currentChar = contents.charAt(tmpIndex);
              if (currentChar == '\n') {
                shouldContinue = false;
                endIndex = tmpIndex;
                tmpIndex++;
                PageElementTag nextTag = analysis.isInTag(tmpIndex);
                if ((nextTag != null) &&
                    (nextTag.getBeginIndex() == tmpIndex) &&
                    isListTag(nextTag)) {
                  replacement =
                      contents.substring(beginIndex, endIndex) +
                      TagBuilder.from(tag.getName(), TagFormat.CLOSE).toString();
                  automatic = true;
                }
              } else if (currentChar == '<') {
                PageElementTag nextTag = analysis.isInTag(tmpIndex);
                if ((nextTag != null) &&
                    (nextTag.getBeginIndex() == tmpIndex) &&
                    isListTag(nextTag)) {
                  shouldContinue = false;
                }
              }
              tmpIndex++;
            }
          }
        }

        // Report error
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, beginIndex, endIndex);
        if (replacement != null) {
          errorResult.addReplacement(replacement, automatic);
        }
        errors.add(errorResult);
      }
    }

    return result;
  }

  /**
   * Test if a tag is a list tag.
   * 
   * @param tag Tag.
   * @return True if it is a list tag.
   */
  private static boolean isListTag(PageElementTag tag) {
    if (tag == null) {
      return false;
    }
    if ((HtmlTagType.LI.equals(tag.getType())) ||
        (HtmlTagType.OL.equals(tag.getType())) ||
        (HtmlTagType.UL.equals(tag.getType()))) {
      return true;
    }
    return false;
  }

  /**
   * Automatic fixing of some errors in the page.
   * 
   * @param analysis Page analysis.
   * @return Page contents after fix.
   */
  @Override
  protected String internalAutomaticFix(PageAnalysis analysis) {
    return fixUsingAutomaticReplacement(analysis);
  }
}
