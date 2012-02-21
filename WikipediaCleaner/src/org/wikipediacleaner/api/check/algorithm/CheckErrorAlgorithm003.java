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
import java.util.Map;

import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.data.PageAnalysis;
import org.wikipediacleaner.api.data.PageContents;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.i18n.GT;

/**
 * Algorithm for analyzing error 3 of check wikipedia project.
 * Error 3: Article with &lt;ref&gt; and no &lt;references /&gt;
 */
public class CheckErrorAlgorithm003 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm003() {
    super("Article with <ref> and no <references />");
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

    // Analyzing the text for <ref>
    boolean refFound = false;
    if (!refFound) {
      // Search for <ref>
      PageElementTag tag = PageContents.findNextTag(
          pageAnalysis.getPage(), pageAnalysis.getContents(), "ref", 0);
      if (tag != null) {
        refFound = true;
      }
    }

    // Analyzing the text for <references>
    boolean referencesFound = false;
    if (refFound) {
      // Search for <references>
      if (!referencesFound) {
        PageElementTag tag = PageContents.findNextTag(
            pageAnalysis.getPage(), pageAnalysis.getContents(), "references", 0);
        if (tag != null) {
          referencesFound = true;
        }
      }

      // Search for templates like {{Références}}
      String templates = pageAnalysis.getWikipedia().getCWConfiguration().getProperty(
          "references_templates", 3, true, true, false);
      String[] referencesTemplates = null;
      if (templates != null) {
        referencesTemplates = pageAnalysis.getWikipedia().convertPropertyToStringArray(templates);
      }
      if (referencesTemplates != null) {
        for (String referencesTemplate : referencesTemplates) {
          if (!referencesFound) {
            PageElementTemplate template = PageContents.findNextTemplate(
                pageAnalysis.getPage(), pageAnalysis.getContents(), referencesTemplate, 0);
            if (template != null) {
              referencesFound = true;
            }
          }
        }
      }
    }

    // Result
    if (refFound && !referencesFound) {
      return true;
    }
    return false;
  }

  /* (non-Javadoc)
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#getParameters()
   */
  @Override
  public Map<String, String> getParameters() {
    Map<String, String> parameters = super.getParameters();
    parameters.put("references_templates", GT._("A list of templates resulting in the inclusion of {0}", "&lt;references/&gt;"));
    return parameters;
  }
}
