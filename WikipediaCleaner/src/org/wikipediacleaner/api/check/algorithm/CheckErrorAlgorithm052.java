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

import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.PageAnalysis;


/**
 * Algorithm for analyzing error 52 of check wikipedia project.
 * Error 52: Category before last headline.
 */
public class CheckErrorAlgorithm052 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm052() {
    super("Category before last headline");
  }

  /**
   * Analyze a page to check if errors are present.
   * 
   * @param pageAnalysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyze(
      PageAnalysis pageAnalysis,
      Collection<CheckErrorResult> errors) {
    if (pageAnalysis == null) {
      return false;
    }

    // Searching for last headline
    String contents = pageAnalysis.getContents();
    int startIndex = contents.length();
    int lastHeadline = -1;
    while ((startIndex >= 0) && (lastHeadline < 0)) {
      int lineIndex = contents.lastIndexOf("=", startIndex);
      if (lineIndex > 0) {
        if ((lineIndex == 0) || (contents.charAt(lineIndex - 1) == '\n')) {
          lastHeadline = lineIndex;
        }
      }
      startIndex = lineIndex - 1;
    }
    Namespace categoryNamespace = Namespace.getNamespace(
        Namespace.CATEGORY, pageAnalysis.getWikipedia().getNamespaces());
    if ((lastHeadline < 0) || (categoryNamespace == null)) {
      return false;
    }

    startIndex = 0;
    boolean result = false;
    while (startIndex < lastHeadline) {

      // Searching for next [[
      int beginIndex = contents.indexOf("[[", startIndex);
      if ((beginIndex < 0) || (beginIndex > lastHeadline)) {
        startIndex = contents.length();
      } else {
        int colonIndex = contents.indexOf(":", beginIndex);
        if (colonIndex >= 0) {
          int endIndex = contents.indexOf("]]", beginIndex);
          if (endIndex > colonIndex) {
            if (categoryNamespace.isPossibleName(contents.substring(beginIndex + 2, colonIndex).trim())) {
              if (errors == null) {
                return true;
              }
              result = true;
              CheckErrorResult errorResult = createCheckErrorResult(
                  pageAnalysis.getPage(), beginIndex, endIndex + 2);
              errors.add(errorResult);
            }
          }
        }
        startIndex = beginIndex + 2;
      }
    }
    return result;
  }
}
