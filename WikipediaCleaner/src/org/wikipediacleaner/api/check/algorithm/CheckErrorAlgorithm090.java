/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.wikipediacleaner.api.check.AddInternalLinkActionProvider;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.constants.ArticleUrl;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.constants.WPCConfiguration;
import org.wikipediacleaner.api.data.DataManager;
import org.wikipediacleaner.api.data.MagicWord;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageAnalysis;
import org.wikipediacleaner.api.data.PageElementExternalLink;
import org.wikipediacleaner.api.data.PageElementImage;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementImage.Parameter;
import org.wikipediacleaner.api.data.PageElementInternalLink;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.i18n.GT;
import org.wikipediacleaner.utils.StringChecker;
import org.wikipediacleaner.utils.StringCheckerUnauthorizedCharacters;


/**
 * Algorithm for analyzing error 090 of check wikipedia project.
 * Error 090: Internal link written as external link
 */
public class CheckErrorAlgorithm090 extends CheckErrorAlgorithmBase {

  /**
   * String checker for text inputed by user.
   */
  private final StringChecker checker;

  public CheckErrorAlgorithm090() {
    super("Internal link written as external link");
    checker = new StringCheckerUnauthorizedCharacters("[]\"");
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

    // Analyze each link
    boolean result = false;
    result |= analyzeExternalLinks(analysis, errors, onlyAutomatic);
    result |= analyzeInternalLinks(analysis, errors, onlyAutomatic);

    return result;
  }

  /**
   * Analyze external links.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param onlyAutomatic True if analysis could be restricted to errors automatically fixed.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeExternalLinks(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors, boolean onlyAutomatic) {

    // Configuration
    String templates = getSpecificProperty("link_templates", true, true, false);
    List<String> linkTemplates = null;
    if (templates != null) {
      linkTemplates = WPCConfiguration.convertPropertyToStringList(templates);
    }
    templates = getSpecificProperty("oldid_templates", true, true, false);
    List<String[]> oldidTemplates = null;
    if (templates != null) {
      oldidTemplates = WPCConfiguration.convertPropertyToStringArrayList(templates);
    }
    templates = getSpecificProperty("history_templates", true, true, false);
    List<String[]> historyTemplates = null;
    if (templates != null) {
      historyTemplates = WPCConfiguration.convertPropertyToStringArrayList(templates);
    }

    // Analyze each external link
    boolean result = false;
    List<PageElementExternalLink> links = analysis.getExternalLinks();
    if (links == null) {
      return result;
    }
    EnumWikipedia wiki = analysis.getWikipedia();
    for (PageElementExternalLink link : links) {
      ArticleUrl articleUrl = ArticleUrl.isArticleUrl(wiki, link.getLink());
      if (articleUrl != null) {
        if (errors == null) {
          return true;
        }
        result = true;
        boolean errorReported = false;
        AnalysisInformation info = new AnalysisInformation(
            analysis, link, articleUrl, errors);

        // Check if link is in image as a link attribute
        if (!errorReported) {
          errorReported = checkImageLink(info);
        }

        // Retrieve information about link
        info.computeLinkInformation();

        // Check if link is in template
        info.computeIsInTemplate(linkTemplates);

        // Restrict automatic modifications
        info.computeIsAutomatic();

        // Check if link is in a timeline tag
        if (!errorReported) {
          errorReported = checkTimeline(info);
        }

        // Check if link is in a ref tag
        if (!errorReported) {
          errorReported = checkRefTag(info);
        }

        // Check special kinds of links
        if (!errorReported) {
          errorReported = checkOldidLink(info, oldidTemplates);
        }
        if (!errorReported) {
          errorReported = checkHistoryLink(info, historyTemplates);
        }

        // Link with text
        if (!errorReported) {
          errorReported = reportLinkWithText(info);
        }

        // Link without text
        if (!errorReported) {
          errorReported = reportLinkWithoutText(info);
        }
      }
    }
    return result;
  }

  /**
   * Analyze cases of external link in image link attribute.
   * 
   * @param info Analysis information.
   * @return True if error has been reported.
   */
  private boolean checkImageLink(AnalysisInformation info) {

    // Check if link is an image link attribute
    int beginIndex = info.link.getBeginIndex();
    PageElementImage image = info.analysis.isInImage(beginIndex);
    if (image == null) {
      return false;
    }
    Parameter imgParameter = image.getParameter(MagicWord.IMG_LINK);
    if (imgParameter == null) {
      return false;
    }
    int beginParam = image.getBeginIndex() + imgParameter.getBeginOffset();
    int endParam = image.getBeginIndex() + imgParameter.getEndOffset();
    if ((beginParam >= beginIndex) || (endParam <= beginIndex)) {
      return false;
    }

    // Decide if replacement can be automatic
    if ((info.articleUrl.getAttributes() != null) ||
        (info.articleUrl.getFragment() != null)) {
      info.automatic = Boolean.FALSE;
    } else  if (info.link.hasSquare()) {
      info.automatic = Boolean.FALSE;
    } else {
      String article = info.articleUrl.getTitle();
      int colonIndex = article.indexOf(':');
      if (colonIndex <= 0) {
        info.automatic = Boolean.FALSE;
      } else if (!Page.areSameTitle(article.substring(colonIndex + 1), image.getImage())) {
        info.automatic = Boolean.FALSE;
      } else {
        Namespace imageNamespace = info.analysis.getWikiConfiguration().getNamespace(Namespace.IMAGE);
        if (!imageNamespace.isPossibleName(article.substring(0, colonIndex).trim())) {
          info.automatic = Boolean.FALSE;
        }
      }
    }

    // Report error
    CheckErrorResult errorResult = createCheckErrorResult(
        info.analysis, beginParam - 1, endParam);
    errorResult.addReplacement("", Boolean.TRUE.equals(info.automatic));
    info.errors.add(errorResult);
    return true;
  }

  /**
   * Analyze cases of link inside timeline tags.
   * 
   * @param info Analysis information.
   * @return True if error has been reported.
   */
  private boolean checkTimeline(
      AnalysisInformation info) {
    PageElementTag timelineTag = info.analysis.getSurroundingTag(
        PageElementTag.TAG_WIKI_TIMELINE, info.beginIndex);
    if (timelineTag == null) {
      return false;
    }
    info.automatic = Boolean.FALSE;
    if (info.isInTemplate) {
      return false;
    }

    boolean timelineOk = true;
    int timelineEnd = info.endIndex;
    if (info.contents.charAt(timelineEnd) == '\"') {
      timelineEnd++;
    }
    int timelineBegin = info.beginIndex;
    if (info.contents.charAt(timelineBegin - 1) == '\"') {
      timelineBegin--;
    }
    while ((timelineBegin > 0) &&
           (info.contents.charAt(timelineBegin - 1) == ' ')) {
      timelineBegin--;
    }
    if (info.contents.startsWith("link:", timelineBegin - "link:".length())) {
      timelineBegin -= "link:".length();
    } else {
      timelineOk = false;
    }
    while ((timelineBegin > 0) &&
           (info.contents.charAt(timelineBegin - 1) == ' ')) {
      timelineBegin--;
    }
    String displayedText = null;
    if (timelineBegin > 0) {
      if (info.contents.charAt(timelineBegin - 1) == '\"') {
        timelineBegin--;
        int tmpIndex = timelineBegin;
        while ((tmpIndex > 0) &&
               ("\"\n".indexOf(info.contents.charAt(tmpIndex - 1)) < 0)) {
          tmpIndex--;
        }
        if ((tmpIndex > 0) &&
            (info.contents.charAt(tmpIndex - 1) == '\"')) {
          displayedText = info.contents.substring(tmpIndex, timelineBegin);
        }
        timelineBegin = tmpIndex - 1;
      } else {
        int tmpIndex = timelineBegin;
        while ((tmpIndex > 0) &&
               (" :".indexOf(info.contents.charAt(tmpIndex - 1)) < 0)) {
          tmpIndex--;
        }
        if (tmpIndex > 0) {
          displayedText = info.contents.substring(tmpIndex, timelineBegin);
        }
        timelineBegin = tmpIndex;
      }
    }
    if (displayedText == null) {
      timelineOk = false;
    }
    while ((timelineBegin > 0) &&
           (info.contents.charAt(timelineBegin - 1) == ' ')) {
      timelineBegin--;
    }
    if (info.contents.startsWith("text:", timelineBegin - "text:".length())) {
      timelineBegin -= "text:".length();
    } else {
      timelineOk = false;
    }

    if (timelineOk) {
      CheckErrorResult errorResult = createCheckErrorResult(
          info.analysis, timelineBegin, timelineEnd);
      errorResult.addReplacement(
          "text:\"" +
          PageElementInternalLink.createInternalLink(
              info.article, info.articleUrl.getFragment(), displayedText) +
          "\"");
      info.errors.add(errorResult);
      return true;
    }

    return false;
  }

  /**
   * Analyze cases of links inside ref tags.
   * 
   * @param info Analysis information.
   * @return True if error has been reported.
   */
  public boolean checkRefTag(AnalysisInformation info) {
    PageElementTag refTag = info.analysis.getSurroundingTag(
        PageElementTag.TAG_WIKI_REF, info.beginIndex);
    if (refTag == null) {
      return false;
    }

    // Determine if the link is the full tag
    boolean full = true;
    int tmpIndex = info.beginIndex;
    while ((tmpIndex > refTag.getValueBeginIndex()) &&
           (info.contents.charAt(tmpIndex - 1) == ' ')) {
      tmpIndex--;
    }
    if (tmpIndex > refTag.getValueBeginIndex()) {
      full = false;
    }
    tmpIndex = info.endIndex;
    while ((tmpIndex < refTag.getValueEndIndex()) &&
           (info.contents.charAt(tmpIndex) == ' ')) {
      tmpIndex++;
    }
    if (tmpIndex < refTag.getValueEndIndex()) {
      full = false;
    }
    if (full) {
      info.automatic = Boolean.FALSE;
    }

    // Check if there's an equivalent link or text before
    tmpIndex = refTag.getCompleteBeginIndex();
    while ((tmpIndex > 0) &&
           (info.contents.charAt(tmpIndex - 1) == ' ')) {
      tmpIndex--;
    }
    if (tmpIndex > 0) {
      PageElementInternalLink previousLink = info.analysis.isInInternalLink(tmpIndex - 1);
      if ((previousLink != null) &&
          Page.areSameTitle(previousLink.getLink(), info.article)) {
        CheckErrorResult errorResult = createCheckErrorResult(
            info.analysis, previousLink.getBeginIndex(), refTag.getCompleteEndIndex());
        errorResult.addReplacement(
            info.contents.substring(previousLink.getBeginIndex(), previousLink.getEndIndex()));
        info.errors.add(errorResult);
        return true;
      } else if (tmpIndex > info.article.length()) {
        String textBefore = info.contents.substring(tmpIndex - info.article.length(), tmpIndex);
        if (Page.areSameTitle(info.article, textBefore)) {
          CheckErrorResult errorResult = createCheckErrorResult(
              info.analysis, tmpIndex - info.article.length(), refTag.getCompleteEndIndex());
          errorResult.addReplacement(
              PageElementInternalLink.createInternalLink(info.article, info.articleUrl.getFragment(), textBefore));
          info.errors.add(errorResult);
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Analyze cases of links with oldid=xxx.
   * 
   * @param info Analysis information.
   * @param oldidTemplates Templates that can be used for links with oldid.
   * @return True if error has been reported.
   */
  private boolean checkOldidLink(
      AnalysisInformation info, List<String[]> oldidTemplates) {

    // Check if old id templates are defined
    if ((oldidTemplates == null) || (oldidTemplates.size() == 0)) {
      return false;
    }

    // Check if it is an old id link
    String oldid = null;
    if (info.articleUrl.getAttributes() != null) {
      for (Map.Entry<String, String> attribute : info.articleUrl.getAttributes().entrySet()) {
        String key = attribute.getKey();
        if ("oldid".equals(key)) {
          oldid = attribute.getValue();
        } else {
          return false;
        }
      }
    }
    if ((oldid == null) || (oldid.isEmpty())) {
      return false;
    }

    CheckErrorResult errorResult = createCheckErrorResult(
        info.analysis, info.beginIndex, info.endIndex);
    for (String[] oldidTemplate : oldidTemplates) {
      if ((oldidTemplate != null) && (oldidTemplate.length > 0)) {
        StringBuilder replacement = new StringBuilder();
        replacement.append("{{");
        replacement.append(oldidTemplate[0]);
        replacement.append("|");
        String articleParam = null;
        if (oldidTemplate.length > 1) {
          if ((oldidTemplate[1].length() > 0) && !"1".equals(oldidTemplate[1])) {
            articleParam = oldidTemplate[1];
          }
        }
        if ((articleParam != null) || (info.article.contains("="))) {
          replacement.append((articleParam != null) ? articleParam : "1");
          replacement.append("=");
        }
        replacement.append(info.article);
        replacement.append("|");
        String oldidParam = null;
        if (oldidTemplate.length > 2) {
          if ((oldidTemplate[2].length() > 0) && !"2".equals(oldidTemplate[2])) {
            oldidParam = oldidTemplate[2];
          }
        }
        if ((oldidParam != null) || (oldid.contains("="))) {
          replacement.append((oldidParam != null) ? oldidParam : "2");
          replacement.append("=");
        }
        replacement.append(oldid);
        replacement.append("|");
        String textParam = null;
        if (oldidTemplate.length > 3) {
          if ((oldidTemplate[3].length() > 0) && !"3".equals(oldidTemplate[3])) {
            textParam = oldidTemplate[3];
          }
        }
        if ((textParam != null) ||
            ((info.text != null) && (info.text.contains("=")))) {
          replacement.append((textParam != null) ? textParam : "3");
          replacement.append("=");
        }
        if (info.text != null) {
          replacement.append(info.text);
        }
        replacement.append("}}");
        errorResult.addReplacement(replacement.toString());

        addBasicReplacement(info, errorResult);
      }
    }
    info.errors.add(errorResult);
    return true;
  }

  /**
   * Analyze cases of links with action=history.
   * 
   * @param info Analysis information.
   * @param historyTemplates Templates that can be used for links with action=history.
   * @return True if error has been reported.
   */
  private boolean checkHistoryLink(
      AnalysisInformation info, List<String[]> historyTemplates) {

    // Check if history templates are defined
    if ((historyTemplates == null) || (historyTemplates.size() == 0)) {
      return false;
    }

    // Check if it is an history link
    boolean isHistory = false;
    if (info.articleUrl.getAttributes() != null) {
      for (Map.Entry<String, String> attribute : info.articleUrl.getAttributes().entrySet()) {
        String key = attribute.getKey();
        if ("action".equals(key)) {
          if (!"history".equals(attribute.getValue())) {
            return false;
          }
          isHistory = true;
        } else {
          return false;
        }
      }
    }
    if (!isHistory) {
      return false;
    }

    CheckErrorResult errorResult = createCheckErrorResult(
        info.analysis, info.beginIndex, info.endIndex);
    for (String[] historyTemplate : historyTemplates) {
      if ((historyTemplate != null) && (historyTemplate.length > 0)) {
        StringBuilder replacement = new StringBuilder();
        replacement.append("{{");
        replacement.append(historyTemplate[0]);
        replacement.append("|");
        String articleParam = null;
        if (historyTemplate.length > 1) {
          if ((historyTemplate[1].length() > 0) && !"1".equals(historyTemplate[1])) {
            articleParam = historyTemplate[1];
          }
        }
        if ((articleParam != null) || (info.article.contains("="))) {
          replacement.append((articleParam != null) ? articleParam : "1");
          replacement.append("=");
        }
        replacement.append(info.article);
        replacement.append("|");
        String textParam = null;
        if (historyTemplate.length > 2) {
          if ((historyTemplate[2].length() > 0) && !"2".equals(historyTemplate[2])) {
            textParam = historyTemplate[2];
          }
        }
        if ((textParam != null) ||
            ((info.text != null) && (info.text.contains("=")))) {
          replacement.append((textParam != null) ? textParam : "3");
          replacement.append("=");
        }
        if (info.text != null) {
          replacement.append(info.text);
        }
        replacement.append("}}");
        errorResult.addReplacement(replacement.toString());

        addBasicReplacement(info, errorResult);
      }
    }
    info.errors.add(errorResult);
    return true;
  }

  /**
   * Basic reporting for a link with text.
   * 
   * @param info Analysis information.
   * @return True if error has been reported.
   */
  private boolean reportLinkWithText(AnalysisInformation info) {
    if (info.text == null) {
      return false;
    }

    CheckErrorResult errorResult = createCheckErrorResult(
        info.analysis, info.beginIndex, info.endIndex);
    addBasicReplacement(info, errorResult);
    info.errors.add(errorResult);
    return true;
  }

  /**
   * Basic reporting for a link without text.
   * 
   * @param info Analysis information.
   * @return True if error has been reported.
   */
  private boolean reportLinkWithoutText(AnalysisInformation info) {
    if (info.text != null) {
      return false;
    }

    // Link without text but previous text
    int tmpIndex = info.beginIndex;
    while ((tmpIndex > 0) &&
           (info.contents.charAt(tmpIndex - 1) == ' ')) {
      tmpIndex--;
    }
    if (tmpIndex > info.article.length()) {
      String textBefore = info.contents.substring(tmpIndex - info.article.length(), tmpIndex);
      if (Page.areSameTitle(info.article, textBefore)) {
        CheckErrorResult errorResult = createCheckErrorResult(
            info.analysis, tmpIndex - info.article.length(), info.endIndex);
        errorResult.addReplacement(
            PageElementInternalLink.createInternalLink(
                (info.needColon ? ":" : "") + info.articleUrl.getTitleAndFragment(), textBefore),
            info.automatic);
        info.errors.add(errorResult);
        return true;
      }
    }

    // Link without text
    CheckErrorResult errorResult = createCheckErrorResult(
        info.analysis, info.beginIndex, info.endIndex);
    String question = GT._("What text should be displayed by the link?");
    AddInternalLinkActionProvider action = new AddInternalLinkActionProvider(
        info.article, info.articleUrl.getFragment(), null, null, null,
        question, info.articleUrl.getTitleAndFragment().replaceAll("\\_", " "), checker);
    errorResult.addPossibleAction(
        GT._("Convert into an internal link"),
        action);
    info.errors.add(errorResult);
    return true;
  }

  /**
   * Add a basic replacement proposal.
   * 
   * @param info Analysis information.
   * @param errorResult Error result.
   */
  private void addBasicReplacement(
      AnalysisInformation info, CheckErrorResult errorResult) {
    errorResult.addReplacement(
        PageElementInternalLink.createInternalLink(
            (info.needColon ? ":" : "") + info.articleUrl.getTitleAndFragment(), info.text),
        info.automatic);
    if (info.text != null) {
      errorResult.addReplacement(info.text);
    }
  }

  /**
   * Analyze internal links.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param onlyAutomatic True if analysis could be restricted to errors automatically fixed.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeInternalLinks(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors, boolean onlyAutomatic) {
    boolean result = false;
    List<PageElementInternalLink> links = analysis.getInternalLinks();
    if (links == null) {
      return result;
    }
    EnumWikipedia wiki = analysis.getWikipedia();
    String host = wiki.getSettings().getHost();
    for (PageElementInternalLink link : links) {
      String target = link.getLink();
      if ((target != null) && (target.length() >= host.length()) &&
          Page.areSameTitle(host, target.substring(0, host.length()))) {
        if (errors == null) {
          return true;
        }
        result = true;
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, link.getBeginIndex(), link.getEndIndex());
        errors.add(errorResult);
      }
    }
    return result;
  }

  /**
   * Automatic fixing of all the errors in the page.
   * 
   * @param analysis Page analysis.
   * @return Page contents after fix.
   */
  @Override
  protected String internalAutomaticFix(PageAnalysis analysis) {
    return fixUsingAutomaticReplacement(analysis);
  }

  /**
   * Bot fixing of all the errors in the page.
   * 
   * @param analysis Page analysis.
   * @return Page contents after fix.
   */
  @Override
  protected String internalBotFix(PageAnalysis analysis) {
    return fixUsingAutomaticBotReplacement(analysis);
  }

  /**
   * @return Map of parameters (Name -> description).
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#getParameters()
   */
  @Override
  public Map<String, String> getParameters() {
    Map<String, String> parameters = super.getParameters();
    parameters.put("history_templates", GT._("Templates to be used for linking to the history of an article"));
    parameters.put("link_templates", GT._("Templates using external links"));
    parameters.put("oldid_templates", GT._("Templates to be used for linking to an old version of an article"));
    return parameters;
  }

  /**
   * Bean for holding analysis information.
   */
  private static class AnalysisInformation {

    /** Page analysis */
    public final PageAnalysis analysis;

    /** Article contents */
    public final String contents;

    /** External link */
    public final PageElementExternalLink link;

    /** Information about article URL */
    public final ArticleUrl articleUrl;

    /** Article title */
    public final String article;

    /** Errors */
    public final Collection<CheckErrorResult> errors;

    /** Begin index */
    public int beginIndex;

    /** End index */
    public int endIndex;

    /** Link text */
    public String text;

    /** True if replacement can be automatic */
    public Boolean automatic;

    /** True if internal needs to be prefixed with a colon */
    public Boolean needColon;

    /** True if link is defined by a template */
    public Boolean isInTemplate;

    public AnalysisInformation(
        PageAnalysis analysis,
        PageElementExternalLink link,
        ArticleUrl articleUrl,
        Collection<CheckErrorResult> errors) {
      this.analysis = analysis;
      this.contents = analysis.getContents();
      this.link = link;
      this.articleUrl = articleUrl;
      this.article = articleUrl.getTitle();
      this.errors = errors;
      this.beginIndex = link.getBeginIndex();
      this.endIndex = link.getEndIndex();
      this.text = link.getText();
      this.automatic = Boolean.TRUE;
      this.needColon = null;
      this.isInTemplate = null;
    }

    /**
     * Compute complementary information about the link
     */
    public void computeLinkInformation() {
      if (link.hasSquare()) {
        if ((beginIndex > 0) && (contents.charAt(beginIndex - 1) == '[') &&
            (endIndex < contents.length()) && (contents.charAt(endIndex) == ']')) {
          beginIndex--;
          endIndex++;
        }
      }
      Page articlePage = DataManager.getPage(
          analysis.getWikipedia(), article, null, null, null);
      needColon = Boolean.FALSE;
      if (articlePage.getNamespace() != null) {
        int ns = articlePage.getNamespace().intValue();
        if (ns % 2 == 0) {
          if ((ns != Namespace.MAIN) &&
              (ns != Namespace.USER) &&
              (ns != Namespace.HELP) &&
              (ns != Namespace.MEDIAWIKI) &&
              (ns != Namespace.TEMPLATE) &&
              (ns != Namespace.WIKIPEDIA)) {
            needColon = Boolean.TRUE;
          }
        }
      }
    }

    /**
     * Compute if link is defined by a template.
     * 
     * @param linkTemplates List of link templates.
     */
    public void computeIsInTemplate(List<String> linkTemplates) {
      
      isInTemplate = Boolean.FALSE;
      if (linkTemplates != null) {
        PageElementTemplate template = analysis.isInTemplate(beginIndex);
        if (template != null) {
          for (String linkTemplate : linkTemplates) {
            String[] elements = linkTemplate.split("\\|");
            if ((elements.length > 2) &&
                Page.areSameTitle(elements[0], template.getTemplateName()) &&
                link.getLink().trim().equals(template.getParameterValue(elements[1]))) {
              text = template.getParameterValue(elements[2]);
              beginIndex = template.getBeginIndex();
              endIndex = template.getEndIndex();
              isInTemplate = true;
            }
          }
        }
      }
    }

    /**
     * Compute restrictions on automatic replacement.
     */
    public void computeIsAutomatic() {
      if (Page.areSameTitle(article, analysis.getPage().getTitle())) {
        automatic = false;
      }
      if (articleUrl.getAttributes() != null) {
        for (Map.Entry<String, String> attribute : articleUrl.getAttributes().entrySet()) {
          String key = attribute.getKey();
          if ("venotify".equals(key)) {
            if (!"created".equals(attribute.getValue())) {
              automatic = false;
            }
          } else if ("action".equals(key)) {
            if (!"edit".equals(attribute.getValue())) {
              automatic = false;
            }
          } else if (!"redlink".equals(key)) {
            automatic = false;
          }
        }
      }
    }
  }
}
