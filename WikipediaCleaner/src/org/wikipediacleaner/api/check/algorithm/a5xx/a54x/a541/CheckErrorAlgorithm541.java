/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a5xx.a54x.a541;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.algorithm.AlgorithmParameter;
import org.wikipediacleaner.api.algorithm.AlgorithmParameterElement;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.LinterCategory;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElementTable;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.TagBuilder;
import org.wikipediacleaner.api.data.contents.tag.TagFormat;
import org.wikipediacleaner.api.data.contents.tag.TagType;
import org.wikipediacleaner.api.data.contents.template.TemplateBuilder;
import org.wikipediacleaner.i18n.GT;


/**
 * Algorithm for analyzing error 541 of check wikipedia project.
 * Error 541: Obsolete tag (see [[Special:LintErrors/obsolete-tag]])
 */
public class CheckErrorAlgorithm541 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm541() {
    super("Obsolete tag");
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

    // Analyze each kind of obsolete tag
    boolean result = false;
    result |= analyzeTags(analysis, errors, HtmlTagType.CENTER);
    result |= analyzeTags(analysis, errors, HtmlTagType.FONT);
    result |= analyzeTags(analysis, errors, HtmlTagType.STRIKE);
    result |= analyzeTags(analysis, errors, HtmlTagType.TT);

    return result;
  }

  /**
   * Analyze a page to check if an obsolete tag is present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param tagType Tag type.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeTags(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      TagType tagType) {

    // Analyze contents to find center tags
    List<PageElementTag> tags = analysis.getTags(tagType);
    if (tags.size() == 0) {
      return false;
    }
    if (errors == null) {
      return true;
    }
    for (PageElementTag tag : tags) {

      // Report incomplete tags
      if (!tag.isComplete()) {
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, tag.getBeginIndex(), tag.getEndIndex());
        errors.add(errorResult);
      }

      // Report complete tags
      if (tag.isComplete() && !tag.isEndTag()) {
        CheckErrorResult errorResult = null;
        if (HtmlTagType.CENTER.equals(tag.getType())) {
          errorResult = analyzeCenterTag(analysis, tag);
        } else if (HtmlTagType.FONT.equals(tag.getType())) {
          errorResult = analyzeFontTag(analysis, tag);
        } else if (HtmlTagType.STRIKE.equals(tag.getType())) {
          errorResult = analyzeStrikeTag(analysis, tag);
        } else if (HtmlTagType.TT.equals(tag.getType())) {
          errorResult = analyzeTtTag(analysis, tag);
        }
        if (errorResult == null) {
          errorResult = createCheckErrorResult(
              analysis, tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
        }
        errors.add(errorResult);
      }
    }

    return true;
  }

  /**
   * @param analysis Page analysis.
   * @param tag Font tag.
   * @return Error for the font tag.
   */
  private CheckErrorResult analyzeFontTag(
      PageAnalysis analysis, PageElementTag tag) {
    
    if (!tag.isComplete() || tag.isFullTag()) {
      return null;
    }

    // Check tag parameters
    boolean automatic = true;
    if ((analysis.getPage() != null) &&
        (analysis.getPage().getNamespace() != null) &&
        (!analysis.getPage().getNamespace().equals(Namespace.MAIN))) {
      automatic = false;
    }
    StringBuilder styleValue = new StringBuilder();
    for (int paramNum = 0; paramNum < tag.getParametersCount(); paramNum++) {
      PageElementTag.Parameter param = tag.getParameter(paramNum);
      if (StringUtils.equals(param.getName(), "color")) {
        styleValue.append(String.format("color: %s;", param.getTrimmedValue()));
      } else if (StringUtils.equals(param.getName(), "face")) {
        styleValue.append(String.format("font-family: %s;", param.getTrimmedValue()));
      } else if (StringUtils.equals(param.getName(), "size")) {
        styleValue.append(String.format("font-size: %s;",  param.getTrimmedValue()));
      } else if (StringUtils.equals(param.getName(), "style")) {
        String style = param.getTrimmedValue();
        if (style != null) {
          String[] elements = style.split(";");
          for (String element : elements) {
            int colonIndex = element.indexOf(':');
            if (colonIndex > 0) {
              String elementName = element.substring(0, colonIndex);
              if (StringUtils.equals(elementName, "color")) {
                styleValue.append(String.format("color: %s;", element.substring(colonIndex + 1)));
              } else if (StringUtils.equals(elementName, "font-family")) {
                styleValue.append(String.format("font-family: %s;", element.substring(colonIndex + 1)));
              } else if (StringUtils.equals(elementName, "font-size")) {
                styleValue.append(String.format("font-size: %s;", element.substring(colonIndex + 1)));
              } else {
                automatic = false;
              }
            }
          }
        }
      } else {
        automatic = false;
      }
    }
    if (styleValue.length() == 0) {
      return null;
    }

    // Analyze surrounding span tag
    PageElementTag spanTag = analysis.getSurroundingTag(HtmlTagType.SPAN, tag.getBeginIndex());
    if ((spanTag != null) &&
        (spanTag.getValueBeginIndex() == tag.getCompleteBeginIndex()) &&
        (spanTag.getValueEndIndex() == tag.getCompleteEndIndex())) {
      CheckErrorResult errorResult = createCheckErrorResult(
          analysis, spanTag.getCompleteBeginIndex(), spanTag.getCompleteEndIndex());
      return errorResult;
    }

    // Analyze surrounding table cell
    PageElementTable table = analysis.isInTable(tag.getBeginIndex());
    if (table != null) {
      PageElementTable.TableCell cell = table.getCellAtIndex(tag.getBeginIndex());
      if (cell != null) {
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, cell.getBeginIndex(), cell.getEndIndex());
        return errorResult;
      }
    }

    // Suggest replacement
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.SPAN,
        "style", styleValue.toString(),
        null, automatic);
    return errorResult;
  }

  /**
   * @param analysis Page analysis.
   * @param tag Center tag.
   * @return Error for the center tag.
   */
  private CheckErrorResult analyzeCenterTag(
      PageAnalysis analysis, PageElementTag tag) {
    String contents = analysis.getContents();

    // Check for center tag inside a table cell
    if (tag.isComplete() && !tag.isFullTag()) {
      int beginIndex = tag.getCompleteBeginIndex();
      int endIndex = tag.getCompleteEndIndex();
      PageElementTable.TableCell tableCell = null;
      PageElementTable table = analysis.isInTable(beginIndex);
      if (table != null) {
        tableCell = table.getCellAtIndex(beginIndex);
      }
      if (tableCell != null) {
        boolean useCell = false;
        if (tableCell.getEndOptionsIndex() <= tableCell.getBeginIndex() + 1) {
          useCell = true;
        } else if (tableCell.getEndOptionsIndex() == tableCell.getBeginIndex() + 2) {
          if (contents.charAt(tableCell.getBeginIndex() + 1) == '|') {
            useCell = true;
          }
        } else {
          // TODO: Handle cell options in TableCell
          if (!contents.substring(tableCell.getBeginIndex(), tableCell.getEndOptionsIndex()).contains("align")) {
            useCell = true;
          }
        }
        if (!useCell) {
          tableCell = null;
        }
      }
      if (tableCell != null) {
        int cellBeginIndex = tableCell.getEndOptionsIndex();
        while ((cellBeginIndex < contents.length()) &&
               (Character.isWhitespace(contents.charAt(cellBeginIndex)))) {
          cellBeginIndex++;
        }
        int cellEndIndex = tableCell.getEndIndex();
        while ((cellEndIndex > 0) &&
               (Character.isWhitespace(contents.charAt(cellEndIndex - 1)))) {
          cellEndIndex--;
        }
        if ((cellBeginIndex == beginIndex) && (cellEndIndex == endIndex)) {
          StringBuilder start = new StringBuilder();
          if (tableCell.getEndOptionsIndex() > tableCell.getBeginIndex() + 2) {
            start.append(contents.substring(tableCell.getBeginIndex(), tableCell.getEndOptionsIndex() - 1));
            if (start.charAt(start.length() - 1) != ' ') {
              start.append(' ');
            }
            start.append("align=\"center\" ");
            start.append(contents.charAt(tableCell.getEndOptionsIndex() - 1));
          } else {
            start.append(contents.substring(tableCell.getBeginIndex(), tableCell.getEndOptionsIndex()));
            start.append(" align=\"center\" |");
          }
          String text = start + "...";
          String replacement =
              start +
              contents.substring(tableCell.getEndOptionsIndex(), tag.getCompleteBeginIndex()) +
              contents.substring(tag.getValueBeginIndex(), tag.getValueEndIndex());
          CheckErrorResult errorResult = createCheckErrorResult(
              analysis, tableCell.getBeginIndex(), tag.getCompleteEndIndex());
          errorResult.addReplacement(replacement, text, true);
          return errorResult;
        }
      }
    }

    // Default replacement: use div tag with style
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
    for (String[] template : centerTemplates) {
      replaceTag(analysis, errorResult, tag, template);
    }
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.DIV,
        "style", "text-align: center;",
        null, false);
    return errorResult;
  }

  /**
   * @param analysis Page analysis.
   * @param tag Strike tag.
   * @return Error for the strike tag.
   */
  private CheckErrorResult analyzeStrikeTag(
      PageAnalysis analysis, PageElementTag tag) {
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.DEL,
        null, null,
        GT._T("for marking an edit"), false);
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.S,
        null, null,
        GT._T("for anything else"), false);
    for (String[] template : strikeTemplates) {
      replaceTag(analysis, errorResult, tag, template);
    }
    return errorResult;
  }

  /**
   * @param analysis Page analysis.
   * @param tag Tt tag.
   * @return Error for the tt tag.
   */
  private CheckErrorResult analyzeTtTag(
      PageAnalysis analysis, PageElementTag tag) {
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.CODE,
        null, null,
        GT._T("preferred for source code"), false);
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.KBD,
        null, null,
        GT._T("preferred for user input"), false);
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.VAR,
        null, null,
        GT._T("preferred for variables"), false);
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.SAMP,
        null, null,
        GT._T("preferred for output, function and tag names, etc."), false);
    for (String[] template : ttTemplates) {
      replaceTag(analysis, errorResult, tag, template);
    }
    replaceTag(
        analysis, errorResult,
        tag, HtmlTagType.SPAN,
        "style", "font-family: monospace;",
        GT._T("preferred for everything else"), false);
    return errorResult;
  }

  /**
   * Handle the replacement of a tag by another tag.
   * 
   * @param analysis Page analysis.
   * @param errorResult Error.
   * @param tag Initial tag.
   * @param tagName Replacement tag name.
   * @param optionName Optional option name for the tag.
   * @param optionValue Optional option value for the tag.
   * @param comment Optional comment.
   * @param automatic True if the replacement should be automatic.
   */
  private void replaceTag(
      PageAnalysis analysis,
      CheckErrorResult errorResult,
      PageElementTag tag, TagType tagType,
      String optionName, String optionValue,
      String comment, boolean automatic) {
    // TODO: Modify to properly use builder from existing tag with modification ?
    TagBuilder openTagBuilder = TagBuilder.from(tagType, TagFormat.OPEN);
    if (optionName != null) {
      openTagBuilder.addAttribute(optionName, optionValue);
    }
    String openTag = openTagBuilder.toString();
    String closeTag = TagBuilder.from(tagType, TagFormat.CLOSE).toString();
    String replacement =
        openTag +
        analysis.getContents().substring(tag.getValueBeginIndex(), tag.getValueEndIndex()) +
        closeTag;
    String text = openTag + "..." + closeTag;
    if (comment != null) {
      text += " (" + comment + ")";
    }
    errorResult.addReplacement(replacement, text, automatic);
  }

  /**
   * Handle the replacement of a template.
   * 
   * @param analysis Page analysis.
   * @param errorResult Error.
   * @param tag Initial tag.
   * @param config Configuration for the replacement.
   */
  private void replaceTag(
      PageAnalysis analysis,
      CheckErrorResult errorResult,
      PageElementTag tag,
      String[] config) {
    if ((config == null) || (config.length == 0)) {
      return;
    }
    String internalText = analysis.getContents().substring(tag.getValueBeginIndex(), tag.getValueEndIndex());
    boolean hasEqual = (internalText.indexOf('=') >= 0);
    String paramName = (config.length > 1 ? config[1] : (hasEqual ? "1=" : ""));
    String replacement = TemplateBuilder.from(config[0]).addParam(paramName, internalText).toString();
    String text = TemplateBuilder.from(config[0]).addParam(paramName, "...").toString();
    boolean automatic = (config.length > 2) ? Boolean.parseBoolean(config[2]) : false;
    if ((config.length > 3) && !config[3].isEmpty()) {
      text += " (" + config[3] + ")";
    }
    errorResult.addReplacement(replacement, text, automatic);
  }

  // ==============================================================================================
  // General functions
  // ==============================================================================================

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

  /** List of templates for replacing &lt;center&gt;...&lt;/center&gt; tags */
  private static final String PARAMETER_CENTER_TEMPLATES = "center_templates";

  /** List of templates for replacing &lt;strike&gt;...&lt;/strike&gt; tags */
  private static final String PARAMETER_STRIKE_TEMPLATES = "strike_templates";

  /** List of templates for replacing &lt;tt&gt;...&lt;/tt&gt; tags */
  private static final String PARAMETER_TT_TEMPLATES = "tt_templates";

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
        if ("obsolete-tag".equals(category.getCategory())) {
          linterCategory = category;
        }
      }
    }

    String tmp = getSpecificProperty(PARAMETER_CENTER_TEMPLATES, true, true, false);
    centerTemplates.clear();
    if (tmp != null) {
      List<String[]> tmpList = WPCConfiguration.convertPropertyToStringArrayList(tmp);
      if (tmpList != null) {
        centerTemplates.addAll(tmpList);
      }
    }

    tmp = getSpecificProperty(PARAMETER_STRIKE_TEMPLATES, true, true, false);
    strikeTemplates.clear();
    if (tmp != null) {
      List<String[]> tmpList = WPCConfiguration.convertPropertyToStringArrayList(tmp);
      if (tmpList != null) {
        strikeTemplates.addAll(tmpList);
      }
    }

    tmp = getSpecificProperty(PARAMETER_TT_TEMPLATES, true, true, false);
    ttTemplates.clear();
    if (tmp != null) {
      List<String[]> tmpList = WPCConfiguration.convertPropertyToStringArrayList(tmp);
      if (tmpList != null) {
        ttTemplates.addAll(tmpList);
      }
    }
  }

  /** Linter category */
  private LinterCategory linterCategory = null;

  /** List of templates for replacing &lt;center&gt;...&lt;/center&gt; tags */
  private final List<String[]> centerTemplates = new ArrayList<>();

  /** List of templates for replacing &lt;strike&gt;...&lt;/strike&gt; tags */
  private final List<String[]> strikeTemplates = new ArrayList<>();

  /** List of templates for replacing &lt;tt&gt;...&lt;/tt&gt; tags */
  private final List<String[]> ttTemplates = new ArrayList<>();

  /**
   * Build the list of parameters for this algorithm.
   */
  @Override
  protected void addParameters() {
    super.addParameters();
    addParameter(new AlgorithmParameter(
        PARAMETER_CENTER_TEMPLATES,
        GT._T("Possible replacements for {0} tags", HtmlTagType.CENTER.getOpenTag()),
        new AlgorithmParameterElement[] {
          new AlgorithmParameterElement(
              "template name",
              GT._T("Template for replacing {0} tag", HtmlTagType.CENTER.getOpenTag())),
          new AlgorithmParameterElement(
              "parameter name",
              GT._T("Parameter to use in the template for the text"),
              true),
          new AlgorithmParameterElement(
              "true/false",
              GT._T("If replacement can be automatic"),
              true),
          new AlgorithmParameterElement(
              "explanation",
              GT._T("Description of the template"),
              true)
        },
        true));
    addParameter(new AlgorithmParameter(
        PARAMETER_STRIKE_TEMPLATES,
        GT._T("Possible replacements for {0} tags", HtmlTagType.STRIKE.getOpenTag()),
        new AlgorithmParameterElement[] {
          new AlgorithmParameterElement(
              "template name",
              GT._T("Template for replacing {0} tag", HtmlTagType.STRIKE.getOpenTag())),
          new AlgorithmParameterElement(
              "parameter name",
              GT._T("Parameter to use in the template for the text"),
              true),
          new AlgorithmParameterElement(
              "true/false",
              GT._T("If replacement can be automatic"),
              true),
          new AlgorithmParameterElement(
              "explanation",
              GT._T("Description of the template"),
              true)
        },
        true));
    addParameter(new AlgorithmParameter(
        PARAMETER_TT_TEMPLATES,
        GT._T("Possible replacements for {0} tags", HtmlTagType.TT.getOpenTag()),
        new AlgorithmParameterElement[] {
          new AlgorithmParameterElement(
              "template name",
              GT._T("Template for replacing {0} tag", HtmlTagType.TT.getOpenTag())),
          new AlgorithmParameterElement(
              "parameter name",
              GT._T("Parameter to use in the template for the text"),
              true),
          new AlgorithmParameterElement(
              "true/false",
              GT._T("If replacement can be automatic"),
              true),
          new AlgorithmParameterElement(
              "explanation",
              GT._T("Description of the template"),
              true)
        },
        true));
  }
}
