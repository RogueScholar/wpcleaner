/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a5xx.a55x.a558;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.algorithm.AlgorithmParameter;
import org.wikipediacleaner.api.algorithm.AlgorithmParameterElement;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.configuration.WPCConfigurationString;
import org.wikipediacleaner.api.configuration.WPCConfigurationStringList;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElement;
import org.wikipediacleaner.api.data.PageElementFullTag;
import org.wikipediacleaner.api.data.PageElementGrouper;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementTag.Parameter;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.IntervalComparator;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.TagType;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.i18n.GT;


/**
 * Algorithm for analyzing error 558 of check wikipedia project.
 * Error 558: Duplicated reference
 */
public class CheckErrorAlgorithm558 extends CheckErrorAlgorithmBase {

  private static final Set<TagType> TAG_SEPARATORS = Stream
      .of(HtmlTagType.SMALL, HtmlTagType.SUB)
      .collect(Collectors.toSet());

  public CheckErrorAlgorithm558() {
    super("Duplicated reference");
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

    // Analyze from the beginning
    List<PageElement> refs = getRefs(analysis);
    if ((refs == null) || (refs.isEmpty())) {
      return false;
    }
    boolean result = false;
    String contents = analysis.getContents();
    int refIndex = 0;
    int maxRefs = refs.size();
    while (refIndex < maxRefs) {

      // Group references separated only by punctuation characters
      int lastRefIndex = grouper.groupElements(refs, refIndex, analysis);
      result |= analyzeGroupOfTags(analysis, contents, errors, refs, refIndex, lastRefIndex);
      refIndex = lastRefIndex + 1;
    }
    return result;
  }

  private static final Pattern CLEAN_PATTERN = Pattern.compile("  ++");

  /**
   * Analyze two tags.
   * 
   * @param analysis Page analysis.
   * @param contents Page contents.
   * @param errors Errors found in the page.
   * @param firstRef First reference.
   * @param nextRef Reference after the first reference.
   * @param previousRef Reference before the second reference.
   * @param secondRef Second reference.
   * @return True if the error was found between the two tags.
   */
  private boolean analyzeTwoTags(
    PageAnalysis analysis, String contents,
    Collection<CheckErrorResult> errors,
    PageElement firstRef,
    PageElement nextRef,
    PageElement previousRef,
    PageElement secondRef) {

    // Check if the two references are identical
    int firstBeginIndex = firstRef.getBeginIndex();
    int firstEndIndex = firstRef.getEndIndex();
    int secondBeginIndex = secondRef.getBeginIndex();
    int secondEndIndex = secondRef.getEndIndex();
    if (firstEndIndex - firstBeginIndex == secondEndIndex - secondBeginIndex) {
      String firstContent = contents.substring(firstBeginIndex, firstEndIndex);
      String secondContent = contents.substring(secondBeginIndex, secondEndIndex);
      if (StringUtils.equals(firstContent, secondContent)) {
        if (errors == null) {
          return true;
        }
        CheckErrorResult errorResult = createCheckErrorResult(analysis, firstRef.getBeginIndex(), secondRef.getEndIndex());
        errorResult.addReplacement(
            contents.substring(firstBeginIndex, previousRef.getEndIndex()),
            canRemoveBetween(contents, previousRef, secondRef));
        errors.add(errorResult);
        return true;
      }
    }

    // Check that the two references are based on tags
    if (!(firstRef instanceof PageElementFullTag) || !(secondRef instanceof PageElementFullTag)) {
      return false;
    }
    PageElementFullTag firstRefTag = (PageElementFullTag) firstRef;
    PageElementFullTag secondRefTag = (PageElementFullTag) secondRef;

    // Check if the two reference tags are for the same name and group
    Parameter firstName = firstRefTag.firstTag.getParameter("name");
    Parameter secondName = secondRefTag.firstTag.getParameter("name");
    boolean sameName = false;
    if ((firstName != null) && (secondName != null) &&
        StringUtils.equals(firstName.getValue(), secondName.getValue())) {
      sameName = true;
    }
    Parameter firstGroup = firstRefTag.firstTag.getParameter("group");
    Parameter secondGroup = secondRefTag.firstTag.getParameter("group");
    boolean sameGroup = false;
    if ((firstGroup != null) && (secondGroup != null) &&
        StringUtils.equals(firstGroup.getValue(), secondGroup.getValue())) {
      sameGroup = true;
    } else if ((firstGroup == null) && (secondGroup == null)) {
      sameGroup = true;
    }

    // Report error if references are for the same name and group
    if (sameName && sameGroup) {
      if (errors == null) {
        return true;
      }
      CheckErrorResult errorResult = createCheckErrorResult(analysis, firstRef.getBeginIndex(), secondRef.getEndIndex());
      if (secondRefTag.firstTag.isFullTag()) {
        errorResult.addReplacement(
            contents.substring(firstBeginIndex, previousRef.getEndIndex()),
            canRemoveBetween(contents, previousRef, secondRef));
      } else if (firstRefTag.firstTag.isFullTag()) {
        errorResult.addReplacement(
            contents.substring(nextRef.getBeginIndex(), secondEndIndex),
            canRemoveBetween(contents, firstRef, nextRef));
      }
      errors.add(errorResult);
      return true;
    }

    // Check if the reference tags contents are similar enough
    if (!firstRefTag.firstTag.isFullTag() && !secondRefTag.firstTag.isFullTag() && sameGroup) {
      String firstContent = contents.substring(firstRefTag.firstTag.getValueBeginIndex(), firstRefTag.firstTag.getValueEndIndex()).trim();
      String secondContent = contents.substring(secondRefTag.firstTag.getValueBeginIndex(), secondRefTag.firstTag.getValueEndIndex()).trim();
      if (!firstContent.isEmpty() && !secondContent.isEmpty() &&
          StringUtils.equals(CLEAN_PATTERN.matcher(firstContent).replaceAll(" "),
                             CLEAN_PATTERN.matcher(secondContent).replaceAll(" "))) {
        if (errors == null) {
          return true;
        }
        CheckErrorResult errorResult = createCheckErrorResult(analysis, firstRef.getBeginIndex(), secondRef.getEndIndex());
        boolean automatic = canRemoveBetween(contents, previousRef, secondRef) && (firstName == null) && (secondName == null);
        errorResult.addReplacement(
            contents.substring(firstBeginIndex, previousRef.getEndIndex()),
            automatic);
        errors.add(errorResult);
        return true;
      }
    }

    return false;
  }

  /**
   * Analyze a group of tags.
   * 
   * @param analysis Page analysis.
   * @param contents Page contents.
   * @param errors Errors found in the page.
   * @param refs List of references.
   * @param firstRefIndex Index of the first reference of the group.
   * @param lastRefIndex Index of the last reference of the group.
   * @return True if the error was found in the group of tags.
   */
  private boolean analyzeGroupOfTags(
      PageAnalysis analysis, String contents,
      Collection<CheckErrorResult> errors,
      List<PageElement> refs,
      int firstRefIndex, int lastRefIndex) {

    if (lastRefIndex == firstRefIndex) {
      return false;
    }
    for (int firstIndex = firstRefIndex; firstIndex < lastRefIndex; firstIndex++) {
      PageElement firstRef = refs.get(firstIndex);
      for (int secondIndex = firstIndex + 1; secondIndex <= lastRefIndex; secondIndex++) {
        if (analyzeTwoTags(
            analysis, contents, errors,
            firstRef, refs.get(firstIndex + 1),
            refs.get(secondIndex - 1), refs.get(secondIndex))) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Check if text can be removed between references.
   * 
   * @param contents Page contents.
   * @param previousRef Previous reference.
   * @param nextRef Next reference.
   * @return True if the text between the two references can be safely removed.
   */
  private boolean canRemoveBetween(
      String contents,
      PageElement previousRef,
      PageElement nextRef) {
    if (nextRef.getBeginIndex() < previousRef.getEndIndex()) {
      return false;
    }
    String text = contents.substring(previousRef.getEndIndex(), nextRef.getBeginIndex());
    return !text.contains("''");
  }

  /**
   * @param analysis Page analysis.
   * @return List of references (tags, templates, ...).
   */
  private List<PageElement> getRefs(PageAnalysis analysis) {
    List<PageElement> refs = new ArrayList<>();

    // Retrieve references defined by tags
    List<PageElementTag> refTags = analysis.getCompleteTags(WikiTagType.REF);
    if (refTags != null) {
      for (PageElementTag refTag : refTags) {
        refs.add(new PageElementFullTag(refTag));
      }
    }

    // Retrieve references defined by templates
    if (!templatesName.isEmpty()) {
      List<PageElementTemplate> templates = analysis.getTemplates();
      for (PageElementTemplate template : templates) {
        if (templatesName.contains(template.getTemplateName())) {
          refs.add(template);
        }
      }
    }

    Collections.sort(refs, new IntervalComparator());
    return refs;
  }

  /**
   * Automatic fixing of all the errors in the page.
   * 
   * @param analysis Page analysis.
   * @return Page contents after fix.
   */
  @Override
  protected String internalAutomaticFix(PageAnalysis analysis) {
    if (!analysis.getPage().isArticle() ||
        !analysis.getPage().isInMainNamespace()) {
      return analysis.getContents();
    }
    return fixUsingAutomaticReplacement(analysis);
  }

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /** Separator between consecutive tags */
  private static final String PARAMETER_SEPARATOR = "separator";

  /** Templates that can replace a tag */
  private static final String PARAMETER_TEMPLATES = "templates";

  /**
   * Initialize settings for the algorithm.
   * 
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    separators.clear();
    separator = getSpecificProperty(PARAMETER_SEPARATOR, true, false, false);
    if (separator == null) {
      separator = getWPCConfiguration().getString(WPCConfigurationString.REF_SEPARATOR);
    }
    if (separator == null) {
      separator = "";
    } else {
      separators.add(separator);
    }
    List<String> tmpList = getWPCConfiguration().getStringList(WPCConfigurationStringList.REF_OTHER_SEPARATORS);
    if (tmpList != null) {
      for (String tmp : tmpList) {
        if (!separators.contains(tmp)) {
          separators.add(tmp);
        }
      }
    }

    String tmp = getSpecificProperty(PARAMETER_TEMPLATES, true, true, false);
    templatesName.clear();
    if (tmp != null) {
      tmpList = WPCConfiguration.convertPropertyToStringList(tmp);
      for (String tmpElement : tmpList) {
        templatesName.add(Page.normalizeTitle(tmpElement));
      }
    }

    grouper = new PageElementGrouper(",;.\'′’-&", separators, TAG_SEPARATORS);
  }

  /** Valid separator between consecutive tags */
  private String separator = "";

  /** Separators between consecutive tags */
  private final List<String> separators = new ArrayList<>();

  /** Templates that can replace a tag */
  private final Set<String> templatesName = new HashSet<>();

  private PageElementGrouper grouper;

  /**
   * Build the list of parameters for this algorithm.
   */
  @Override
  protected void addParameters() {
    super.addParameters();
    addParameter(new AlgorithmParameter(
        PARAMETER_SEPARATOR,
        GT._T("Used as a separator between consecutive {0} tags", "&lt;ref&gt;"),
        new AlgorithmParameterElement(
            "text",
            GT._T("Used as a separator between consecutive {0} tags", "&lt;ref&gt;"))));
    addParameter(new AlgorithmParameter(
        PARAMETER_TEMPLATES,
        GT._T("Templates that can be used to replace {0} tags", "&lt;ref&gt;"),
        new AlgorithmParameterElement(
            "template name",
            GT._T("Template that can be used to replace {0} tags", "&lt;ref&gt;")),
        true));
  }
}
