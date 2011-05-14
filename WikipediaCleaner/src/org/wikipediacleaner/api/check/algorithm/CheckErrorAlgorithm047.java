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
import org.wikipediacleaner.api.data.PageAnalysis;


/**
 * Algorithm for analyzing error 47 of check wikipedia project.
 * Error 47: Template not correct begin
 */
public class CheckErrorAlgorithm047 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm047() {
    super("Template not correct begin");
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

    // Analyze contents from the beginning by counting {{ and }}
    int startIndex = 0;
    boolean result = false;
    int levelCurlyBrackets = 0;
    int levelMath = 0;
    int previousCurlyBracket = -1;
    String contents = pageAnalysis.getContents();
    while (startIndex < contents.length()) {
      switch (contents.charAt(startIndex)) {
      case '<':
        // Check if <math> or </math> tag
        if (contents.startsWith("<math>", startIndex)) {
          levelMath++;
        } else if (contents.startsWith("</math>", startIndex)) {
          levelMath--;
        }
        break;
      case '{':
        // Check if {{
        if (levelMath == 0) {
          if (((startIndex + 1) < contents.length()) &&
              (contents.charAt(startIndex + 1) == '{')) {
            levelCurlyBrackets++;
            startIndex++;
          } else {
            previousCurlyBracket = startIndex;
          }
        }
        break;
      case '}':
        // Check if }}
        if (levelMath == 0) {
          if (((startIndex + 1) < contents.length()) &&
              (contents.charAt(startIndex + 1) == '}')) {
            if (levelCurlyBrackets == 0) {
              if (errors == null) {
                return true;
              }
              result = true;
              CheckErrorResult errorResult = null;
              if (previousCurlyBracket < 0) {
                errorResult = createCheckErrorResult(
                    pageAnalysis.getPage(), startIndex, startIndex + 2);
              } else {
                errorResult = createCheckErrorResult(
                    pageAnalysis.getPage(), previousCurlyBracket, startIndex + 2);
                errorResult.addReplacement(
                    "{" + contents.substring(previousCurlyBracket, startIndex + 2));
              }
              errors.add(errorResult);
            } else {
              levelCurlyBrackets--;
            }
            startIndex++;
          }
        }
        break;
      }
      startIndex++;
    }
    return result;
  }
}
