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
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;


/**
 * Algorithm for analyzing error 63 of check wikipedia project.
 * Error 63: HTML text style element &lt;small&gt; in ref, sub or sup
 */
public class CheckErrorAlgorithm063 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm063() {
    super("HTML text style element <small> in ref, sub or sup");
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
    if (!analysis.getPage().isArticle()) {
      return false;
    }

    // Analyze each <small> tag
    boolean result = false;
    List<PageElementTag> smallTags = analysis.getTags(HtmlTagType.SMALL);
    for (PageElementTag smallTag : smallTags) {
      int index = smallTag.getBeginIndex();
      PageElementTag refTag = analysis.getSurroundingTag(WikiTagType.REF, index);
      PageElementTag subTag = analysis.getSurroundingTag(HtmlTagType.SUB, index);
      PageElementTag supTag = analysis.getSurroundingTag(HtmlTagType.SUP, index);
      if ((refTag != null) || (subTag != null) || (supTag != null)) {
        if (errors == null) {
          return true;
        }
        result = true;
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis,
            smallTag.getBeginIndex(), smallTag.getEndIndex());
        errors.add(errorResult);
      }
    }

    return result;
  }
}
