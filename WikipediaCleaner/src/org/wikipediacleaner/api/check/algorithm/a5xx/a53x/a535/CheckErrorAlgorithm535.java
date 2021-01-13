/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a5xx.a53x.a535;

import java.util.Collection;
import java.util.List;

import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.LinterCategory;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElementExternalLink;
import org.wikipediacleaner.api.data.PageElementInternalLink;
import org.wikipediacleaner.api.data.PageElementInterwikiLink;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.ilink.InternalLinkBuilder;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;


/**
 * Algorithm for analyzing error 535 of check wikipedia project.
 * Error 535: Tidy font bug (see [[Special:LintErrors/tidy-font-bug]])
 */
public class CheckErrorAlgorithm535 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm535() {
    super("Tidy bug affecting font tags wrapping links");
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
    if ((analysis == null) || (analysis.getPage() == null)) {
      return false;
    }

    // Analyze each font tag
    List<PageElementTag> fontTags = analysis.getCompleteTags(HtmlTagType.FONT);
    String contents = analysis.getContents();
    boolean result = false;
    for (PageElementTag fontTag : fontTags) {

      // Analyze font tag
      if (fontTag.isComplete() &&
          !fontTag.isEndTag() &&
          !fontTag.isFullTag() &&
          (fontTag.getMatchingTag() != null)) {

        // Trim value of font tag
        int valueBeginIndex = fontTag.getValueBeginIndex();
        int valueEndIndex = fontTag.getValueEndIndex();
        while ((valueBeginIndex < valueEndIndex) &&
               ("\n ".indexOf(contents.charAt(valueBeginIndex)) >= 0)) {
          valueBeginIndex++;
        }
        while ((valueBeginIndex < valueEndIndex) &&
               ("\n ".indexOf(contents.charAt(valueEndIndex - 1)) >= 0)) {
          valueEndIndex--;
        }

        // Quick check of what inside the font tag
        boolean couldReport = false;
        boolean automatic = true;
        if ((valueBeginIndex < valueEndIndex) &&
            (contents.charAt(valueBeginIndex) == '[') &&
            (contents.charAt(valueEndIndex - 1) == ']')) {
          couldReport = true;
        }

        // Check the attributes of the font tag
        if (couldReport) {
          couldReport = false;
          for (int paramNum = 0; paramNum < fontTag.getParametersCount(); paramNum++) {
            PageElementTag.Parameter param = fontTag.getParameter(paramNum);
            String paramName = param.getName();
            if (paramName.equalsIgnoreCase("color")) {
              couldReport = true;
            } else {
              automatic = false;
            }
          }
        }

        // Check what is in the font tag
        if (couldReport) {

          // Initializations
          String openFont = contents.substring(fontTag.getBeginIndex(), fontTag.getEndIndex());
          PageElementTag closeTag = fontTag.getMatchingTag();
          String closeFont = contents.substring(closeTag.getBeginIndex(), closeTag.getEndIndex());

          // Internal link
          PageElementInternalLink iLink = analysis.isInInternalLink(valueBeginIndex);
          if ((iLink != null) &&
              (iLink.getBeginIndex() == valueBeginIndex) &&
              (iLink.getEndIndex() == valueEndIndex)) {

            // Report error
            result = true;
            if (errors == null) {
              return result;
            }
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, fontTag.getCompleteBeginIndex(), fontTag.getCompleteEndIndex());
            String replacement =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              InternalLinkBuilder
                .from(iLink.getLink())
                .withAnchor(iLink.getAnchor())
                .withText(openFont + iLink.getDisplayedText() + closeFont)
                .toString() +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            String text =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              InternalLinkBuilder.from(iLink.getLink()).withText(openFont + "..." + closeFont).toString() +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            errorResult.addReplacement(replacement, text, automatic);
            errors.add(errorResult);
          }

          // External link
          PageElementExternalLink eLink = analysis.isInExternalLink(valueBeginIndex);
          if ((eLink != null) &&
              (eLink.getBeginIndex() == valueBeginIndex) &&
              (eLink.getEndIndex() == valueEndIndex) &&
              (eLink.hasSquare())) {

            // Report error
            result = true;
            if (errors == null) {
              return result;
            }
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, fontTag.getCompleteBeginIndex(), fontTag.getCompleteEndIndex());
            String replacement =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              PageElementExternalLink.createExternalLink(
                eLink.getLink(),
                openFont + eLink.getDisplayedText() + closeFont) +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            String text =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              PageElementExternalLink.createExternalLink(
                eLink.getLink(),
                openFont + "..." + closeFont) +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            errorResult.addReplacement(replacement, text, automatic);
            errors.add(errorResult);
          }

          // Interwiki link
          PageElementInterwikiLink iwLink = analysis.isInInterwikiLink(valueBeginIndex);
          if ((iwLink != null) &&
              (iwLink.getBeginIndex() == valueBeginIndex) &&
              (iwLink.getEndIndex() == valueEndIndex)) {

            // Report error
            result = true;
            if (errors == null) {
              return result;
            }
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, fontTag.getCompleteBeginIndex(), fontTag.getCompleteEndIndex());
            String replacement =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              PageElementInterwikiLink.createInterwikiLink(
                iwLink.getInterwikiText(), iwLink.getLink(), iwLink.getAnchor(),
                openFont + iwLink.getText() + closeFont) +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            String text =
              contents.substring(fontTag.getValueBeginIndex(), valueBeginIndex) +
              PageElementInterwikiLink.createInterwikiLink(
                iwLink.getInterwikiText(), iwLink.getLink(), iwLink.getAnchor(),
                openFont + "..." + closeFont) +
              contents.substring(valueEndIndex, fontTag.getValueEndIndex());
            errorResult.addReplacement(replacement, text, automatic);
            errors.add(errorResult);
          }
        }
      }
    }

    return result;
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

  /**
   * @return True if the error has a special list of pages.
   */
  @Override
  public boolean hasSpecialList() {
    return (linterCategory != null);
  }

  /**
   * Retrieve the list of pages in error.
   * 
   * @param wiki Wiki.
   * @param limit Maximum number of pages to retrieve.
   * @return List of pages in error.
   */
  @Override
  public List<Page> getSpecialList(EnumWikipedia wiki, int limit) {
    List<Page> result = null;
    if (linterCategory != null) {
      API api = APIFactory.getAPI();
      try {
        result = api.retrieveLinterCategory(
            wiki, linterCategory.getCategory(),
            Namespace.MAIN, false, true, limit);
      } catch (APIException e) {
        //
      }
    }
    return result;
  }

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /**
   * Initialize settings for the algorithm.
   * 
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    List<LinterCategory> categories = getWikiConfiguration().getLinterCategories();
    if (categories != null) {
      for (LinterCategory category : categories) {
        if ("tidy-font-bug".equals(category.getCategory())) {
          linterCategory = category;
        }
      }
    }
  }

  /** Linter category */
  private LinterCategory linterCategory = null;
}