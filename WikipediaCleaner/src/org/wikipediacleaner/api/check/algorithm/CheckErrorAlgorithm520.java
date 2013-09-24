/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm;

import java.util.Collection;

import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.PageAnalysis;


/**
 * Algorithm for analyzing error 520 of check wikipedia project.
 * Error 520: Pawns (♙) in main namespace
 */
public class CheckErrorAlgorithm520 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm520() {
    super("Pawns");
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
    if ((analysis == null) || (analysis.getPage() == null)) {
      return false;
    }
    Integer ns = analysis.getPage().getNamespace();
    if ((ns == null) || (ns.intValue() != Namespace.MAIN)) {
      return false;
    }

    // Search pawns
    String contents = analysis.getContents();
    int index = contents.indexOf('♙');
    boolean result = false;
    while (index >= 0) {
      if (errors == null) {
        return true;
      }
      result = true;
      CheckErrorResult errorResult = createCheckErrorResult(
          analysis.getPage(), index, index + 1);
      errorResult.addReplacement("");
      errors.add(errorResult);
      index = contents.indexOf('♙', index + 1);
    }

    return result;
  }
}