/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a0xx.a06x.a069;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.wikipediacleaner.api.algorithm.AlgorithmParameter;
import org.wikipediacleaner.api.algorithm.AlgorithmParameterElement;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.CheckErrorResult.ErrorLevel;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmISBN;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.configuration.WPCConfigurationStringList;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElement;
import org.wikipediacleaner.api.data.PageElementExternalLink;
import org.wikipediacleaner.api.data.PageElementFunction;
import org.wikipediacleaner.api.data.PageElementISBN;
import org.wikipediacleaner.api.data.PageElementInternalLink;
import org.wikipediacleaner.api.data.PageElementInterwikiLink;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.PageElementTemplate.Parameter;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.ContentsUtil;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.api.data.contents.template.TemplateBuilder;
import org.wikipediacleaner.i18n.GT;


/**
 * Algorithm for analyzing error 69 of check wikipedia project.
 * Error 69: ISBN wrong syntax
 */
public class CheckErrorAlgorithm069 extends CheckErrorAlgorithmISBN {

  public CheckErrorAlgorithm069() {
    super("ISBN wrong syntax");
  }

  /** List of strings that could be before an ISBN in <nowiki>. */
  private final static String[] EXTEND_BEFORE_NOWIKI = {
    WikiTagType.NOWIKI.getOpenTag(),
    HtmlTagType.SMALL.getOpenTag(),
    "(",
  };

  /** List of strings that could be after an ISBN in <nowiki>. */
  private final static String[] EXTEND_AFTER_NOWIKI = {
    WikiTagType.NOWIKI.getCloseTag(),
    HtmlTagType.SMALL.getCloseTag(),
    ")",
  };

  /** List of strings that could be between "ISBN" and its value. */
  private final static String[] FIRST_SEPARATOR = {
    "&nbsp;",
    "&#x20;",
  };

  private final static Map<String, String> PREFIX_INTERNAL_LINK = new HashMap<>();

  static {
    // Configure PREFIX_INTERNAL_LINK
    PREFIX_INTERNAL_LINK.put(PageElementISBN.ISBN_PREFIX, "");
    PREFIX_INTERNAL_LINK.put("(" + PageElementISBN.ISBN_PREFIX, "(");
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

    // Analyze each ISBN
    boolean result = false;
    result |= analyzeISBNs(analysis, errors);

    // Report also ISBN like [[International Standard Book Number|ISBN]]&nbsp;978-0321637734
    result |= analyzeInternalLinks(analysis, errors);

    // Report also ISBN inside <nowiki> tags
    result |= analyzeNowikiTags(analysis, errors);

    // Report also ISBN in interwiki links
    result |= analyzeInterwikiLinks(analysis, errors);

    return result;
  }

  /**
   * Analyze page to check if an error is present in ISBNs.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyzeISBNs(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {

    boolean result = false;
    List<PageElementISBN> isbns = analysis.getISBNs();
    for (PageElementISBN isbn : isbns) {
      boolean reported = analyzeISBN(analysis, errors, isbn);
      result |= reported;

      // Analyze to find links to Special/BookSources
      if (!reported) {
        reported = analyzeISBNInSpecialBookSources(analysis, errors, isbn);
        result |= reported; 
      }

      // Analyze if ISBN is inside an external link
      if (!reported) {
        reported = analyzeISBNInExternalLink(analysis, errors, isbn);
        result |= reported;
      }
    }

    return result;
  }

  /**
   * Analyze an ISBN to check if has an error.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return isbn ISBN to be checked.
   */
  private boolean analyzeISBN(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      PageElementISBN isbn) {

    // Check if an error is detected in the ISBN
    if (isbn.isCorrect() || !isbn.isValid()) {
      return false;
    }
    if (shouldIgnoreError(analysis, isbn)) {
      return false;
    }

    // Exclude special configured values for ISBN if inside a template parameter
    if (isbn.isTemplateParameter()) {
      WPCConfiguration config = analysis.getWPCConfiguration();
      List<String[]> specialValues = config.getStringArrayList(
          WPCConfigurationStringList.ISBN_SPECIAL_VALUES);
      if ((specialValues != null) && !specialValues.isEmpty()) {
        PageElementTemplate template = analysis.isInTemplate(isbn.getBeginIndex());
        if (template != null) {
          Parameter param = template.getParameterAtIndex(isbn.getBeginIndex());
          if ((param != null) &&
              (param.getName() != null) &&
              (param.getName().trim().length() > 0)) {
            String name = param.getName().trim();
            for (String[] specialValue : specialValues) {
              if ((specialValue.length > 2) &&
                  (Page.areSameTitle(template.getTemplateName(), specialValue[0])) &&
                  (name.equals(specialValue[1])) &&
                  (isbn.getISBNNotTrimmed().equals(specialValue[2]))) {
                return false; 
              }
            }
          }
        }
      }
    }

    // Exclude parameters in templates
    if (isbn.isTemplateParameter() &&
        analysis.isInNamespace(Namespace.TEMPLATE)) {
      PageElementTemplate template = analysis.isInTemplate(isbn.getBeginIndex());
      if (template != null) {
        Parameter param = template.getParameterAtIndex(isbn.getBeginIndex());
        if (param != null) {
          List<PageElementFunction> functions = analysis.getFunctions();
          if (functions != null) {
            for (PageElementFunction function : functions) {
              int functionIndex = function.getBeginIndex();
              if ((template == analysis.isInTemplate(functionIndex)) &&
                  (param == template.getParameterAtIndex(functionIndex))) {
                return false;
              }
            }
          }
        }
      }
    }

    // Report error
    if (errors == null) {
      return true;
    }

    final ISBNExtension extension = ISBNExtension.of(analysis, isbn.getBeginIndex(), isbn.getEndIndex());
    CheckErrorResult errorResult = createCheckErrorResult(analysis, isbn, false);
    String prefix = null;
    String suffix = null;
    if ((extension.beginIndex < isbn.getBeginIndex()) &&
        (extension.endIndex > isbn.getEndIndex())) {
      final String contents = analysis.getContents();
      prefix = contents.substring(extension.beginIndex, isbn.getBeginIndex());
      suffix = contents.substring(isbn.getEndIndex(), extension.endIndex);
      errorResult = createCheckErrorResult(
          analysis,
          extension.beginIndex, extension.endIndex,
          errorResult.getErrorLevel());
    }
    addSuggestions(analysis, errorResult, isbn);
    errors.add(errorResult);
    List<String> replacements = isbn.getCorrectISBN();
    if (replacements != null) {
      for (String replacement : replacements) {
        if (!replacement.equals(analysis.getContents().substring(isbn.getBeginIndex(), isbn.getEndIndex()))) {
          if ((prefix != null) && (suffix != null)) {
            errorResult.addReplacement(prefix + replacement + suffix);
          }
          errorResult.addReplacement(replacement);
        }
      }
    }

    return true;
  }

  /**
   * Analyze an ISBN to check if it is in a Special/BookSources.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return isbn ISBN to be checked.
   */
  private boolean analyzeISBNInSpecialBookSources(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      PageElementISBN isbn) {

    // Check if ISBN is in a Special/BookSources
    if (isbn.isTemplateParameter()) {
      return false;
    }
    PageElement element = null;
    ErrorLevel level = ErrorLevel.CORRECT;
    String isbnText = analysis.getContents().substring(isbn.getBeginIndex(), isbn.getEndIndex());
    PageElementInternalLink link = analysis.isInInternalLink(isbn.getBeginIndex());
    if ((link != null) && (isbnText.equals(link.getText()))) {
      level = isSpecialBookSources(analysis, link.getLink());
      if (level != ErrorLevel.CORRECT) {
        element = link;
      }
    }
    if (element == null) {
      PageElementInterwikiLink iwLink = analysis.isInInterwikiLink(isbn.getBeginIndex());
      if ((iwLink != null) && (isbnText.equals(iwLink.getText()))) {
        level = isSpecialBookSources(analysis, iwLink.getLink());
        if (level != ErrorLevel.CORRECT) {
          element = iwLink;
        }
      }
    }
    if (element == null) {
      return false;
    }

    // Report error
    if (errors == null) {
      return true;
    }
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, element.getBeginIndex(), element.getEndIndex(), level);
    List<String> replacements = isbn.getCorrectISBN();
    for (String replacement : replacements) {
      errorResult.addReplacement(replacement);
    }
    errors.add(errorResult);

    return true;
  }

  /**
   * Analyze an ISBN to check if it is in an external link.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return isbn ISBN to be checked.
   */
  private boolean analyzeISBNInExternalLink(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      PageElementISBN isbn) {

    // Check if ISBN is in an external link
    if (isbn.isTemplateParameter()) {
      return false;
    }
    PageElementExternalLink link = analysis.isInExternalLink(isbn.getBeginIndex());
    if ((link == null) || !link.hasSquare() ||
        (isbn.getBeginIndex() < link.getBeginIndex() + link.getTextOffset()) ||
        (link.getText() == null)) {
      return false;
    }

    // Report error
    if (errors == null) {
      return true;
    }
    
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, link.getBeginIndex(), link.getEndIndex());
    int beginIndex = isbn.getBeginIndex();
    int realEndIndex = isbn.getEndIndex();
    String contents = analysis.getContents();
    while ((beginIndex > 0) &&
           (" ,;.(".indexOf(contents.charAt(beginIndex - 1)) >= 0)) {
      beginIndex--;
    }
    if (realEndIndex < link.getEndIndex()) {
      int tmpIndex = realEndIndex;
      while ((tmpIndex < link.getEndIndex()) &&
             (", ".indexOf(contents.charAt(tmpIndex)) >= 0)) {
        tmpIndex++;
      }
      if ((tmpIndex < link.getEndIndex()) &&
          (contents.startsWith(isbn.getISBN(), tmpIndex))) {
        realEndIndex = tmpIndex + isbn.getISBN().length();
      }
    }
    int endIndex = realEndIndex;
    while ((endIndex < link.getEndIndex()) &&
           (")".indexOf(contents.charAt(endIndex)) >= 0)) {
      endIndex++;
    }
    if (beginIndex > link.getBeginIndex() + link.getTextOffset()) {
      String replacementPrefix =
          contents.substring(link.getBeginIndex(), beginIndex) +
          contents.substring(endIndex, link.getEndIndex()) +
          contents.substring(beginIndex, isbn.getBeginIndex());
      String textPrefix =
          contents.substring(link.getBeginIndex(), link.getBeginIndex() + 7) +
          "...]" +
          contents.substring(beginIndex, isbn.getBeginIndex());
      List<String> replacements = isbn.getCorrectISBN();
      for (String replacement : replacements) {
        errorResult.addReplacement(
            replacementPrefix + replacement + contents.substring(realEndIndex, endIndex),
            textPrefix + replacement + contents.substring(realEndIndex, endIndex));
      }
      errorResult.addReplacement(
          replacementPrefix + contents.substring(isbn.getBeginIndex(), isbn.getEndIndex()),
          textPrefix + contents.substring(isbn.getBeginIndex(), isbn.getEndIndex()));
      if (endIndex < link.getEndIndex()) {
        replacementPrefix =
            contents.substring(link.getBeginIndex(), beginIndex) +
            "]" +
            contents.substring(beginIndex, isbn.getBeginIndex());
        for (String replacement : replacements) {
          errorResult.addReplacement(
              replacementPrefix + replacement + contents.substring(isbn.getEndIndex(), link.getEndIndex() - 1),
              textPrefix + replacement + contents.substring(isbn.getEndIndex(), link.getEndIndex() - 1));
        }
        errorResult.addReplacement(
            replacementPrefix + contents.substring(isbn.getBeginIndex(), link.getEndIndex() - 1),
            textPrefix + contents.substring(isbn.getBeginIndex(), link.getEndIndex() - 1));
      }
    } else if (endIndex >= link.getEndIndex() - 1) {
      List<String> replacements = isbn.getCorrectISBN();
      for (String replacement : replacements) {
        errorResult.addReplacement(replacement);
      }
      errorResult.addReplacement(contents.substring(isbn.getBeginIndex(), isbn.getEndIndex()));
    }
    errors.add(errorResult);

    return true;
  }

  /**
   * Analyze internal links to check if an ISBN error is present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyzeInternalLinks(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {

    List<PageElementInternalLink> links = analysis.getInternalLinks();
    if (links == null) {
      return false;
    }

    boolean result = false;
    for (PageElementInternalLink link : links) {
      if (analyzeInternalLinkPrefix(analysis, errors, link)) {
        result = true;
      } else {
        result |= analyzeInternalLinkInterwiki(analysis, errors, link);
      }
    }

    return result;
  }

  /**
   * Analyze an internal link to check if an ISBN error is present due to ISBN prefix.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param link Internal link to be checked.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeInternalLinkPrefix(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      PageElementInternalLink link) {

    // Check for the presence of the ISBN prefix
    String extraPrefix = PREFIX_INTERNAL_LINK.get(link.getDisplayedText().trim());
    if (extraPrefix == null) {
      return false;
    }

    // Move to the beginning of the potential ISBN value
    int tmpIndex = link.getEndIndex();
    String contents = analysis.getContents();
    boolean shouldContinue = true;
    while (shouldContinue) {
      shouldContinue = false;
      if (tmpIndex < contents.length()) {
        if (" \u00A0".indexOf(contents.charAt(tmpIndex)) >= 0) {
          tmpIndex++;
          shouldContinue = true;
        } else {
          for (String separator : FIRST_SEPARATOR) {
            if (contents.startsWith(separator, tmpIndex)) {
              tmpIndex += separator.length();
              shouldContinue = true;
            }
          }
        }
      }
    }

    // Analyze if there's an ISBN value
    boolean isbnFound = false;
    int beginISBN = tmpIndex;
    String suffix = null;
    if (tmpIndex < contents.length()) {
      PageElementInternalLink nextLink = null;
      PageElementExternalLink nextLinkE = null;
      if (contents.charAt(tmpIndex) == '[') {
        nextLink = analysis.isInInternalLink(tmpIndex);
        if (nextLink != null) {
          int offset = nextLink.getTextOffset();
          if (offset > 0) {
            tmpIndex += offset;
          } else {
            tmpIndex += 2;
          }
        } else {
          nextLinkE = analysis.isInExternalLink(tmpIndex);
          if (nextLinkE != null) {
            int offset = nextLinkE.getTextOffset();
            if (offset > 0) {
              tmpIndex += offset;
            } else {
              tmpIndex += 1;
            }
          }
        }
      }
      boolean endFound = false;
      while (!endFound) {
        endFound = true;
        if ((tmpIndex < contents.length()) && (contents.charAt(tmpIndex) == '<')) {
          PageElementTag tag = analysis.isInTag(tmpIndex);
          if ((tag != null) && (tag.getBeginIndex() == tmpIndex)) {
            tmpIndex = tag.getEndIndex();
            endFound = false;
          }
        }
      }
      if ((tmpIndex < contents.length()) &&
          (PageElementISBN.POSSIBLE_CHARACTERS.indexOf(contents.charAt(tmpIndex)) >= 0)) {
        isbnFound = true;
      }
      if (nextLink != null) {
        suffix = nextLink.getDisplayedText();
        tmpIndex = nextLink.getEndIndex();
      } else if (nextLinkE != null) {
        suffix = nextLinkE.getDisplayedText();
        tmpIndex = nextLinkE.getEndIndex();
      } else {
        while ((tmpIndex < contents.length()) &&
               ((PageElementISBN.POSSIBLE_CHARACTERS.indexOf(contents.charAt(tmpIndex)) >= 0) ||
                (PageElementISBN.EXTRA_CHARACTERS.indexOf(contents.charAt(tmpIndex)) >= 0 ))) {
          tmpIndex++;
        }
        suffix = contents.substring(beginISBN, tmpIndex);
      }
    }
    if (!isbnFound) {
      return false;
    }

    // Report error
    if (errors == null) {
      return true;
    }

    // Handle CX2 bug
    if ("ISBN (identifier)".equals(link.getLink())) {
      int previousIndex = ContentsUtil.moveIndexBackwardWhileFound(contents, link.getBeginIndex() - 1, " ");
      PageElementTemplate previousTemplate = analysis.isInTemplate(previousIndex);
      if ((previousTemplate != null) &&
          (previousTemplate.getEndIndex() == previousIndex + 1) &&
          ("ISBN".equals(previousTemplate.getTemplateName())) &&
          (Objects.equals(suffix, previousTemplate.getParameterValue(0)))) {
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, link.getBeginIndex(), tmpIndex);
        errorResult.addReplacement("", true);
        errors.add(errorResult);
        return true;
      }
    }

    reportError(analysis, errors, link.getBeginIndex(), tmpIndex, extraPrefix, suffix);
    return true;
  }

  /**
   * Analyze an internal link to check if an ISBN error is present due to similarity with an interwiki link.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param link Internal link to be checked.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeInternalLinkInterwiki(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      PageElementInternalLink link) {

    // Check that the target of the links contains a name space separator.
    String target = link.getLink();
    int colonIndex = target.indexOf(':');
    if (colonIndex < 0) {
      return false;
    }

    // Check each pair Special/Book sources to see if it matches
    String prefix = target.substring(0, colonIndex);
    String suffix = target.substring(colonIndex + 1);
    int slashIndex = suffix.indexOf('/');
    String suffix2 = (slashIndex > 0) ? suffix.substring(0, slashIndex) : suffix;
    for (Pair<Set<String>, Set<String>> bookSource : BookSources.MAP.values()) {
      boolean prefixFound = false;
      for (String possiblePrefix : bookSource.getLeft()) {
        prefixFound |= Page.areSameTitle(prefix, possiblePrefix);
      }
      if (prefixFound) {
        for (String possibleSuffix : bookSource.getRight()) {
          if (Page.areSameTitle(suffix, possibleSuffix) ||
              Page.areSameTitle(suffix2, possibleSuffix)) {
            if (errors == null) {
              return true;
            }
            CheckErrorResult errorResult  = createCheckErrorResult(analysis, link.getBeginIndex(), link.getEndIndex());
            errorResult.addReplacement(link.getDisplayedText());
            errors.add(errorResult);
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * Analyze nowiki tags to check if an ISBN is present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyzeNowikiTags(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {

    List<PageElementTag> nowikiTags = analysis.getCompleteTags(WikiTagType.NOWIKI);
    if (nowikiTags == null) {
      return false;
    }

    boolean result = false;
    String contents = analysis.getContents();
    for (PageElementTag nowikiTag : nowikiTags) {
      if (!nowikiTag.isFullTag() && nowikiTag.isComplete()) {
        String nowikiContent = contents.substring(
            nowikiTag.getValueBeginIndex(), nowikiTag.getValueEndIndex());
        int index = 0;
        while (index < nowikiContent.length()) {
          if (nowikiContent.startsWith(PageElementISBN.ISBN_PREFIX, index)) {
            int tmpIndex = index + PageElementISBN.ISBN_PREFIX.length();
            boolean hasSeparator = false;
            while ((tmpIndex < nowikiContent.length()) && 
                   (PageElementISBN.EXTRA_CHARACTERS.indexOf(nowikiContent.charAt(tmpIndex)) >= 0)) {
              hasSeparator = true;
              tmpIndex++;
            }
            boolean hasCharacter = false;
            int indexCharacter = tmpIndex;
            boolean shouldContinue = true;
            while (shouldContinue) {
              int tmpIndex2 = tmpIndex;
              shouldContinue = false;
              while ((tmpIndex2 < nowikiContent.length()) &&
                     (PageElementISBN.EXTRA_CHARACTERS.indexOf(nowikiContent.charAt(tmpIndex2)) >= 0)) {
                tmpIndex2++;
              }
              while ((tmpIndex2 < nowikiContent.length()) &&
                     (PageElementISBN.POSSIBLE_CHARACTERS.indexOf(nowikiContent.charAt(tmpIndex2)) >= 0)) {
                hasCharacter = true;
                shouldContinue = true;
                tmpIndex2++;
              }
              if (shouldContinue) {
                tmpIndex = tmpIndex2;
              }
            }
            if (hasSeparator && hasCharacter) {
              if (errors == null) {
                return true;
              }
              result = true;

              // Try to extend area
              int beginIndex = nowikiTag.getValueBeginIndex() + index;
              boolean extensionFound = false;
              do {
                extensionFound = false;
                for (String before : EXTEND_BEFORE_NOWIKI) {
                  if ((beginIndex >= before.length()) &&
                      (contents.startsWith(before, beginIndex - before.length()))) {
                    extensionFound = true;
                    beginIndex -= before.length();
                  }
                }
              } while (extensionFound);
              int endIndex = nowikiTag.getValueBeginIndex() + tmpIndex;
              do {
                extensionFound = false;
                for (String after : EXTEND_AFTER_NOWIKI) {
                  if ((endIndex < contents.length()) &&
                      (contents.startsWith(after, endIndex))) {
                    extensionFound = true;
                    endIndex += after.length();
                  }
                }
              } while (extensionFound);

              // Report error
              CheckErrorResult errorResult = createCheckErrorResult(
                  analysis, beginIndex, endIndex);
              if ((beginIndex <= nowikiTag.getCompleteBeginIndex()) &&
                  (endIndex >= nowikiTag.getCompleteEndIndex())) {
                errorResult.addReplacement(contents.substring(
                    nowikiTag.getValueBeginIndex() + index,
                    nowikiTag.getValueBeginIndex() + tmpIndex));
                List<String[]> isbnTemplates = analysis.getWPCConfiguration().getStringArrayList(
                    WPCConfigurationStringList.ISBN_TEMPLATES);
                if (isbnTemplates != null) {
                  for (String[] isbnTemplate : isbnTemplates) {
                    if (isbnTemplate.length > 2) {
                      String templateName = isbnTemplate[0];
                      String[] params = isbnTemplate[1].split(",");
                      Boolean suggested = Boolean.valueOf(isbnTemplate[2]);
                      if ((params.length > 0) && (Boolean.TRUE.equals(suggested))) {
                        TemplateBuilder builder = TemplateBuilder.from(templateName);
                        builder.addParam(
                            !"1".equals(params[0]) ? params[0] : null,
                                nowikiContent.substring(indexCharacter, tmpIndex));
                        errorResult.addReplacement(builder.toString());
                      }
                    }
                  }
                }
              }
              errors.add(errorResult);
              index = tmpIndex;
            } else {
              index += PageElementISBN.ISBN_PREFIX.length();
            }
          } else {
            index++;
          }
        }
      }
    }

    return result;
  }

  /**
   * Analyze interwiki links to check if an ISBN is present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  public boolean analyzeInterwikiLinks(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {

    List<PageElementInterwikiLink> iwLinks = analysis.getInterwikiLinks();
    if (iwLinks == null) {
      return false;
    }

    boolean result = false;
    for (PageElementInterwikiLink iwLink : iwLinks) {
      String link = iwLink.getLink();
      int anchorIndex = link.indexOf(':');
      if (anchorIndex > 0) {
        String namespace = link.substring(0, anchorIndex);
        String iwText = iwLink.getInterwikiText();
        Pair<Set<String>, Set<String>> wiki = BookSources.MAP.get(iwText);
        if ("Special".equals(namespace) ||
            ((wiki != null) &&
             (wiki.getLeft() != null) &&
             wiki.getLeft().contains(namespace))) {
          String target = link.substring(anchorIndex + 1);
          int slashIndex = target.indexOf('/');
          if (slashIndex > 0) {
            target = target.substring(0, slashIndex);
          }
          target.replaceAll("_", " ");
          if ("BookSources".equals(target) ||
              ((wiki != null) &&
               (wiki.getRight() != null) &&
               (wiki.getRight().contains(target)))) {
            if (errors == null) {
              return true;
            }
            result = true;
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, iwLink.getBeginIndex(), iwLink.getEndIndex());
            if (iwLink.getText() != null) {
              errorResult.addReplacement(iwLink.getText());
            }
            errors.add(errorResult);
          }
        }
      }
    }

    return result;
  }

  /**
   * Report an error on an ISBN.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param initialBeginIndex Begin index of the area where the error is found.
   * @param initialEndIndex End index of the area where the error is found.
   * @param extraPrefix Text before the ISBN.
   * @param suffix ISBN value
   * @return Flag indicating if the error was found.
   */
  private void reportError(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors,
      int initialBeginIndex, int initialEndIndex,
      String extraPrefix, String suffix) {

    boolean tryTemplate = PageElementISBN.isValid(suffix);
    if (tryTemplate) {
  
      // Report error if extension is symmetric
      final ISBNExtension extension = ISBNExtension.of(analysis, initialBeginIndex, initialEndIndex);
      if ((extension.parenthesisBefore == extension.parenthesisAfter) &&
          (extension.squareBracketBefore == extension.squareBracketAfter) &&
          (extension.smallBefore == extension.smallAfter) &&
          (StringUtils.isEmpty(extraPrefix))) {
        for (UseTemplate useTemplate : useTemplates) {
          if ((extension.parenthesisBefore > 0 == useTemplate.includesParenthesis) &&
              (extension.squareBracketBefore == 0) &&
              (extension.smallBefore > 0 == useTemplate.includesSmall)) {
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, extension.beginIndex, extension.endIndex);
            errorResult.addReplacement(
                TemplateBuilder.from(useTemplate.templateName).addParam(suffix).toString() + extension.textAfter,
                true);
            errors.add(errorResult);
            return;
          }
        }
      }
    }

    // Report error
    CheckErrorResult errorResult = createCheckErrorResult(analysis, initialBeginIndex, initialEndIndex);
    errorResult.addReplacement(
        extraPrefix +
        PageElementISBN.ISBN_PREFIX +
        " " +
        suffix);
    errors.add(errorResult);
  }

  /**
   * @param analysis Page analysis.
   * @param link Link destination.
   * @return Error level.
   */
  private ErrorLevel isSpecialBookSources(PageAnalysis analysis, String link) {
    if (link == null) {
      return ErrorLevel.CORRECT;
    }
    int colonIndex = link.indexOf(':');
    if (colonIndex == 0) {
      link = link.substring(1);
      colonIndex = link.indexOf(':');
    }
    if (colonIndex > 0) {
      Namespace special = analysis.getWikiConfiguration().getNamespace(Namespace.SPECIAL);
      String prefix = link.substring(0, colonIndex);
      if ((special != null) && (special.isPossibleName(prefix))) {
        if (link.startsWith("BookSources", colonIndex + 1)) {
          return ErrorLevel.ERROR;
        }
        return ErrorLevel.WARNING;
      }
    }
    return ErrorLevel.CORRECT;
  }

  /**
   * @param isbn ISBN number.
   * @return Reason for the error.
   */
  @Override
  public String getReason(PageElementISBN isbn) {
    if (isbn == null) {
      return null;
    }
    return reason;
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

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /** Reason for the error */
  private static final String PARAMETER_REASON = "reason";

  /** Templates to use to replace ISBN */
  private static final String PARAMETER_USE_TEMPLATE = "use_template";
  
  /**
   * Initialize settings for the algorithm.
   * 
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    reason = getSpecificProperty(PARAMETER_REASON, true, true, false);
    
    String tmp = getSpecificProperty(PARAMETER_USE_TEMPLATE, true, true, false);
    useTemplates.clear();
    if (tmp != null) {
      WPCConfiguration.convertPropertyToStringArrayList(tmp).stream()
          .map(UseTemplate::of)
          .filter(Objects::nonNull)
          .forEach(useTemplates::add);
    }
  }

  /** Reason for the error */
  private String reason = null;

  /** Templates to use to replace ISBN */
  private final List<UseTemplate> useTemplates = new ArrayList<>();

  /**
   * Build the list of parameters for this algorithm.
   */
  @Override
  protected void addParameters() {
    super.addParameters();
    addParameter(new AlgorithmParameter(
        PARAMETER_REASON,
        GT._T("An explanation of the problem"),
        new AlgorithmParameterElement(
            "text",
            GT._T("An explanation of the problem"))));
    addParameter(new AlgorithmParameter(
        PARAMETER_USE_TEMPLATE,
        GT._T("Templates to replace ISBN"),
        new AlgorithmParameterElement[] {
            new AlgorithmParameterElement(
                "template name",
                GT._T("Name of the template")),
            new AlgorithmParameterElement(
                "parenthesis",
                GT._T("True if the template includes parenthesis")),
            new AlgorithmParameterElement(
                "small",
                GT._T("True if the template includes small tags"))
        },
        true));
  }
}
