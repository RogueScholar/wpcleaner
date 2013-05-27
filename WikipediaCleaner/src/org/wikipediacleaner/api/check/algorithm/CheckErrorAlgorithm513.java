/*
 *  WikipediaCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2008  Nicolas Vervelle
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.wikipediacleaner.api.check.algorithm;

import java.util.Collection;
import java.util.List;

import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.data.PageAnalysis;
import org.wikipediacleaner.api.data.PageElementExternalLink;
import org.wikipediacleaner.api.data.PageElementInternalLink;


/**
 * Algorithm for analyzing error 511 of check wikipedia project.
 * Error 513: Internal link inside external link
 */
public class CheckErrorAlgorithm513 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm513() {
    super("Internal link inside external link");
  }

  /**
   * Analyze a page to check if errors are present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyze(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {
    if ((analysis == null) || (analysis.getInternalLinks() == null)) {
      return false;
    }

    // Analyze each external link
    boolean result = false;
    List<PageElementExternalLink> links = analysis.getExternalLinks();
    if (links == null) {
      return result;
    }
    String contents = analysis.getContents();
    for (PageElementExternalLink link : links) {
      if ((link.hasSquare()) &&
          (link.getText() != null) &&
          (!link.hasSecondSquare())) {
        PageElementInternalLink internalLink = analysis.isInInternalLink(link.getEndIndex());
        if ((internalLink != null) &&
            (internalLink.getBeginIndex() == link.getEndIndex())) {
          if (errors == null) {
            return true;
          }
          result = true;
          CheckErrorResult errorResult = createCheckErrorResult(
              analysis.getPage(), link.getBeginIndex(), internalLink.getEndIndex());
          errorResult.addReplacement(
              contents.substring(link.getBeginIndex(), internalLink.getBeginIndex()) +
              internalLink.getDisplayedTextNotTrimmed());
          errors.add(errorResult);
        }
      }
    }

    return result;
  }
}
