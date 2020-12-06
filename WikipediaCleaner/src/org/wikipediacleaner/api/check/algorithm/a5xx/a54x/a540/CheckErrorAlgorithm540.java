/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a5xx.a54x.a540;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.data.PageElement;
import org.wikipediacleaner.api.data.PageElementExternalLink;
import org.wikipediacleaner.api.data.PageElementFormatting;
import org.wikipediacleaner.api.data.PageElementFormattingAnalysis;
import org.wikipediacleaner.api.data.PageElementImage;
import org.wikipediacleaner.api.data.PageElementListItem;
import org.wikipediacleaner.api.data.PageElementParagraph;
import org.wikipediacleaner.api.data.PageElementTable;
import org.wikipediacleaner.api.data.PageElementInternalLink;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.PageElementTemplate.Parameter;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.PageElementTitle;
import org.wikipediacleaner.api.data.contents.ContentsElement;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;
import org.wikipediacleaner.gui.swing.component.MWPane;
import org.wikipediacleaner.i18n.GT;


/**
 * Algorithm for analyzing error 540 of check wikipedia project.
 * Error 540: Missing end bold/italic (see [[Special:LintErrors/missing-end-tag]])
 */
public class CheckErrorAlgorithm540 extends CheckErrorAlgorithmBase {

  /** Possible global fixes */
  private final static String[] globalFixes = new String[] {
    GT._T("Fix bold and italic"),
  };

  public CheckErrorAlgorithm540() {
    super("Missing bold/italic end tag");
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

    // Analyze formatting elements
    boolean result = false;
    result |= analyzeFormatting(analysis, errors);

    return result;
  }

  // ==============================================================================================
  // Formatting elements analysis
  // ==============================================================================================

  /**
   * Analyze a page to check if formatting errors are present.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @return Flag indicating if the error was found.
   */
  private boolean analyzeFormatting(
      PageAnalysis analysis,
      Collection<CheckErrorResult> errors) {

    // Analyze contents to find formatting elements
    boolean result = false;
    List<PageElementFormatting> initialElements = PageElementFormatting.listFormattingElements(analysis);
    if (initialElements.isEmpty()) {
      return result;
    }
    List<PageElementFormatting> elements = new ArrayList<>(initialElements);

    // Remove correct formatting tags from the list
    List<PageElementFormatting> reportElements = new ArrayList<>();
    boolean shouldContinue = true;
    while (shouldContinue) {
      shouldContinue = false;

      // Check tags
      List<PageElementTag> tags = analysis.getTags();
      for (PageElementTag tag : tags) {
        if (tag.isComplete() && !tag.isFullTag()) {
          shouldContinue |= analyzeCorrectArea(
              elements, reportElements,
              tag.getValueBeginIndex(), tag.getValueEndIndex(),
              tag.getCompleteBeginIndex(), tag.getCompleteEndIndex());
        }
      }

      // Check internal links
      List<PageElementInternalLink> iLinks = analysis.getInternalLinks();
      for (PageElementInternalLink iLink : iLinks) {
        int beginIndex = iLink.getBeginIndex();
        int endIndex = iLink.getEndIndex() - 2;
        if (iLink.getText() != null) {
          beginIndex += iLink.getTextOffset();
        } else {
          beginIndex =  endIndex;
        }
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            beginIndex, endIndex,
            iLink.getBeginIndex(), iLink.getEndIndex());
      }

      // Check images
      List<PageElementImage> images = analysis.getImages();
      for (PageElementImage image : images) {
        PageElementImage.Parameter paramDesc = image.getDescriptionParameter();
        int beginIndex = image.getBeginIndex();
        int endIndex = image.getBeginIndex();
        if (paramDesc != null) {
          beginIndex += paramDesc.getBeginOffset();
          endIndex += paramDesc.getEndOffset();
        }
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            beginIndex, endIndex,
            image.getBeginIndex(), image.getEndIndex());
      }

      // Check external links
      List<PageElementExternalLink> eLinks = analysis.getExternalLinks();
      for (PageElementExternalLink eLink : eLinks) {
        int beginIndex = eLink.getBeginIndex();
        int endIndex = eLink.getEndIndex() - 1;
        if (eLink.getText() != null) {
          beginIndex += eLink.getTextOffset();
        } else {
          beginIndex = endIndex;
        }
        if (eLink.hasSquare() && eLink.hasSecondSquare()) {
          shouldContinue |= analyzeCorrectArea(
              elements, reportElements,
              beginIndex, endIndex,
              eLink.getBeginIndex(), eLink.getEndIndex());
        }
      }

      // Check titles
      List<PageElementTitle> titles = analysis.getTitles();
      for (PageElementTitle title : titles) {
        int beginIndex = title.getBeginIndex() + title.getFirstLevel();
        int endIndex = title.getEndIndex() - title.getSecondLevel();
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            beginIndex, endIndex,
            title.getBeginIndex(), title.getEndIndex());
      }

      // Check templates
      List<PageElementTemplate> templates = analysis.getTemplates();
      for (PageElementTemplate template : templates) {
        int beginIndex = template.getEndIndex() - 2;
        int endIndex = template.getEndIndex() - 2;
        if (template.getParameterCount() > 0) {
          beginIndex = template.getParameterPipeIndex(0);
        }
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            beginIndex, endIndex,
            template.getBeginIndex(), template.getEndIndex());
        for (int paramNum = 0; paramNum < template.getParameterCount(); paramNum++) {
          Parameter param = template.getParameter(paramNum);
          shouldContinue |= analyzeCorrectArea(
              elements, reportElements,
              param.getBeginIndex(), param.getEndIndex(),
              param.getBeginIndex(), param.getEndIndex());
        }
      }

      // Check list items
      List<PageElementListItem> items = analysis.getListItems();
      for (PageElementListItem item : items) {
        int beginIndex = item.getBeginIndex() + item.getDepth();
        int endIndex = item.getEndIndex();
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            beginIndex, endIndex,
            item.getBeginIndex(), item.getEndIndex());
      }

      // Check tables
      List<PageElementTable> tables = analysis.getTables();
      for (PageElementTable table : tables) {

        // Check table title
        PageElementTable.TableCaption caption = table.getTableCaption();
        if (caption != null) {
          shouldContinue |= analyzeCorrectArea(
              elements, reportElements,
              caption.getBeginIndex() + 2, caption.getEndIndex(),
              caption.getBeginIndex(), caption.getEndIndex());
        }

        // Check table cells
        for (PageElementTable.TableLine line : table.getTableLines()) {
          for (PageElementTable.TableCell cell : line.getCells()) {
            shouldContinue |= analyzeCorrectArea(
                elements, reportElements,
                cell.getEndOptionsIndex(), cell.getEndIndex(),
                cell.getBeginIndex(), cell.getEndIndex());
          }
        }
      }

      // Check paragraphs
      List<PageElementParagraph> paragraphs = analysis.getParagraphs();
      for (PageElementParagraph paragraph : paragraphs) {
        shouldContinue |= analyzeCorrectArea(
            elements, reportElements,
            paragraph.getBeginIndex(), paragraph.getEndIndex(),
            paragraph.getBeginIndex(), paragraph.getEndIndex());
      }
    }

    // Report all errors
    for (PageElementFormatting element : reportElements) {
      if (errors == null) {
        return true;
      }
      result = true;
      reportFormattingElement(analysis, initialElements, element, errors);
    }

    // Report all formatting elements left
    //for (FormattingElement element : elements) {
    //  if (errors == null) {
    //    return true;
    //  }
    //  result = true;
    //  CheckErrorResult errorResult = createCheckErrorResult(
    //      analysis, element.index, element.index + element.length, ErrorLevel.WARNING);
    //  errors.add(errorResult);
    //}

    return result;
  }

  /**
   * Analyze an area to exclude correct parts.
   * 
   * @param elements List of formatting elements.
   * @param reportElements List of formatting elements to report.
   * @param beginAnalysis Begin index for the analysis.
   * @param endAnalysis End index for the analysis.
   * @param beginArea Begin index for the area.
   * @param endArea End index for the area.
   * @return True if modifications have been done.
   */
  private boolean analyzeCorrectArea(
      List<PageElementFormatting> elements,
      List<PageElementFormatting> reportElements,
      int beginAnalysis, int endAnalysis,
      int beginArea, int endArea) {

    // Analyze area for formatting elements
    PageElementFormattingAnalysis formattingArea = PageElementFormattingAnalysis.analyzeArea(elements, beginArea, endArea);
    if (formattingArea.getBoldCount() + formattingArea.getItalicCount() == 0) {
      return false;
    }

    // If only one, there's a problem that can already be reported
    if ((formattingArea.getBoldCount() + formattingArea.getItalicCount() == 1) ||
        (formattingArea.getElements().size() == 1)) {
      for (PageElementFormatting element : formattingArea.getElements()) {
        reportElements.add(element);
      }
      PageElementFormatting.excludeArea(elements, beginArea, endArea);
      return true;
    }

    // Analyze area
    PageElementFormattingAnalysis formattingAnalysis = formattingArea;
    if ((beginAnalysis != beginArea) || (endAnalysis != endArea)) {
      formattingAnalysis = PageElementFormattingAnalysis.analyzeArea(elements, beginAnalysis, endAnalysis);
    }
    if (formattingAnalysis.getBoldCount() + formattingAnalysis.getItalicCount() == 0) {
      return false;
    }

    // If only one there's a problem that can already be reported
    if (formattingArea.getBoldCount() + formattingArea.getItalicCount() == 1) {
      for (PageElementFormatting element : formattingAnalysis.getElements()) {
        reportElements.add(element);
      }
      PageElementFormatting.excludeArea(elements, beginAnalysis, endAnalysis);
    }

    // Check that every element is in the same area
    PageElementFormatting firstElement = formattingAnalysis.getElements().get(0);
    boolean sameArea = true;
    for (PageElementFormatting element : formattingAnalysis.getElements()) {
      sameArea &= PageElementFormatting.areInSameArea(firstElement, element);
    }
    if (!sameArea) {
      return false;
    }

    // Clear area if correct
    int countOutside =
        formattingArea.getBoldCount() + formattingArea.getItalicCount() -
        formattingAnalysis.getBoldCount() - formattingAnalysis.getItalicCount();
    if ((formattingAnalysis.getBoldCount() + formattingAnalysis.getItalicCount()) % 2 == 0) {
      if (countOutside % 2 != 0) {
        PageElementFormatting.excludeArea(elements, beginAnalysis, endAnalysis);
      } else {
        PageElementFormatting.excludeArea(elements, beginArea, endArea);
      }
      return true;
    }

    // If all formats are the same, report the last one
    if ((formattingAnalysis.getBoldCount() == 0) ||
        (formattingAnalysis.getItalicCount() == 0)) {
      reportElements.add(formattingAnalysis.getElements().get(formattingAnalysis.getElements().size() - 1));
      if (countOutside % 2 != 0) {
        PageElementFormatting.excludeArea(elements, beginAnalysis, endAnalysis);
      } else {
        PageElementFormatting.excludeArea(elements, beginArea, endArea);
      }
      return true;
    }

    // Report one
    reportElements.add(formattingAnalysis.getElements().get(formattingAnalysis.getElements().size() - 1));
    if (countOutside % 2 != 0) {
      PageElementFormatting.excludeArea(elements, beginAnalysis, endAnalysis);
    } else {
      PageElementFormatting.excludeArea(elements, beginArea, endArea);
    }
    return true;
  }

  /**
   * @param analysis Page analysis.
   * @param elements Formatting elements.
   * @param element Formatting element to report.
   * @param errors List of errors.
   */
  private void reportFormattingElement(
      PageAnalysis analysis,
      List<PageElementFormatting> elements,
      PageElementFormatting element,
      Collection<CheckErrorResult> errors) {

    // Report inside redirects
    if ((analysis.getPage() != null) &&
        (analysis.getPage().getRedirects().isRedirect()) &&
        (elements.size() == 1)) {
      if (reportFormattingElement(
          analysis, elements, element, errors,
          element.getIndex(), element.getIndex() + element.getLength(),
          element.getIndex(), element.getIndex() + element.getLength(),
          true, false, false, true)) {
        return;
      }
    }

    // Try to report for each surrounding element
    List<ContentsElement> surroundingElements = element.getSurroundingElements();
    for (ContentsElement surroundingElement : surroundingElements) {

      // Report inside a list item
      if (surroundingElement instanceof PageElementListItem) {
        // NOTE: closeFull=true fixes lines incorrectly when the closing wasn't intended at the end
        PageElementListItem listItem = (PageElementListItem) surroundingElement;
        if (reportFormattingElement(
            analysis, elements, element, errors,
            listItem.getBeginIndex() + listItem.getDepth(), listItem.getEndIndex(),
            listItem.getBeginIndex(), listItem.getEndIndex(),
            true, false, false, true)) {
          return;
        }
      }

      // Report inside an internal link
      if (surroundingElement instanceof PageElementInternalLink) {
        PageElementInternalLink iLink = (PageElementInternalLink) surroundingElement;
        if (reportFormattingElement(
            analysis, elements, element, errors,
            iLink.getBeginIndex() + iLink.getTextOffset(), iLink.getEndIndex() - 2,
            iLink.getBeginIndex(), iLink.getEndIndex(),
            false, false, true, true)) {
          return;
        }
      }

      // Report inside an image
      if (surroundingElement instanceof PageElementImage) {
        PageElementImage image = (PageElementImage) surroundingElement;
        PageElementImage.Parameter paramDesc = image.getDescriptionParameter();
        if (paramDesc != null) {
          if (reportFormattingElement(
              analysis, elements, element, errors,
              image.getBeginIndex() + paramDesc.getBeginOffset(),
              image.getBeginIndex() + paramDesc.getEndOffset(),
              image.getBeginIndex() + paramDesc.getBeginOffset() - 1,
              image.getBeginIndex() + paramDesc.getEndOffset(),
              true, false, true, true)) {
            return;
          }
        }
      }

      // Report inside an external link
      if (surroundingElement instanceof PageElementExternalLink) {
        PageElementExternalLink eLink = (PageElementExternalLink) surroundingElement;
        if (eLink.hasSquare() && eLink.hasSecondSquare()) {
          if (reportFormattingElement(
              analysis, elements, element, errors,
              eLink.getBeginIndex() + eLink.getTextOffset(), eLink.getEndIndex() - 1,
              eLink.getBeginIndex(), eLink.getEndIndex(),
              false, false, true, true)) {
            return;
          }
        }
      }

      // Report inside a title
      if (surroundingElement instanceof PageElementTitle) {
        PageElementTitle title = (PageElementTitle) surroundingElement;
        if (reportFormattingElement(
            analysis, elements, element, errors,
            title.getBeginIndex() + title.getFirstLevel(),
            title.getEndIndex() - title.getSecondLevel(),
            title.getBeginIndex(), title.getEndIndex(),
            true, false, true, true)) {
          return;
        }
      }

      // Report inside a reference tag
      if (surroundingElement instanceof PageElementTag) {
        PageElementTag refTag = (PageElementTag) surroundingElement;
        if (PageElementTag.TAG_WIKI_REF.equals(refTag.getNormalizedName())) {
          if (reportFormattingElement(
              analysis, elements, element, errors,
              refTag.getValueBeginIndex(), refTag.getValueEndIndex(),
              refTag.getValueBeginIndex(), refTag.getValueEndIndex(),
              false, true, false, true)) {
            return;
          }
        }
      }

      // Report inside a table caption
      if (surroundingElement instanceof PageElementTable.TableCaption) {
        PageElementTable.TableCaption caption = (PageElementTable.TableCaption) surroundingElement;
        if (reportFormattingElement(
            analysis, elements, element, errors,
            caption.getBeginIndex() + 2, caption.getEndIndex(),
            caption.getBeginIndex(), caption.getEndIndex(),
            true, false, true, true)) {
          return;
        }
      }

      // Report inside a table cell
      if (surroundingElement instanceof PageElementTable.TableCell) {
        PageElementTable.TableCell cell = (PageElementTable.TableCell) surroundingElement;
        if (element.getIndex() < cell.getEndOptionsIndex()) {
          if (reportFormattingElementInCellOptions(
              analysis, elements, element, errors, cell)) {
            return;
          }
        }
        if (reportFormattingElement(
            analysis, elements, element, errors,
            cell.getEndOptionsIndex(), cell.getEndIndex(),
            cell.getEndOptionsIndex(), cell.getEndIndex(),
            true, true, true, true)) {
          return;
        }
      }

      // Report inside a template
      if (surroundingElement instanceof PageElementTemplate.Parameter) {
        PageElementTemplate.Parameter templateParam = (PageElementTemplate.Parameter) surroundingElement;
        PageElementTemplate template = analysis.isInTemplate(templateParam.getValueStartIndex());
        // TODO: See if area can be reduced on the parameter value, but see problems with {{shy}} on enWP for example
        if (reportFormattingElement(
            analysis, elements, element, errors,
            templateParam.getValueStartIndex(), templateParam.getEndIndex(),
            template.getBeginIndex(), template.getEndIndex(),
            !StringUtils.isEmpty(templateParam.getName()), false, true, true)) {
          return;
        }
      }
    }

    // Default report
    reportError(
        analysis, element,
        element.getIndex(), element.getIndex() + element.getLength(),
        errors);
  }

  /**
   * @param analysis Page analysis.
   * @param element Formatting element to report.
   * @param beginIndex Beginning index of the area.
   * @param endIndex End index of the area.
   * @param errors List of errors.
   * @param errors
   */
  private void reportError(
      PageAnalysis analysis, PageElementFormatting element,
      int beginIndex, int endIndex,
      Collection<CheckErrorResult> errors) {

    // Reduce area
    beginIndex = moveBeginIndex(analysis, beginIndex, endIndex, true);
    endIndex = moveEndIndex(analysis, beginIndex, endIndex);

    // Analyze text in the area
    int completeBeginIndex = beginIndex;
    int completeEndIndex = endIndex;
    String contents = analysis.getContents();
    PageElement closeElement = getElementAfter(analysis, element, false);
    if (closeElement != null) {
      completeEndIndex = Math.max(completeEndIndex, closeElement.getEndIndex());
    }

    // Report error
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, completeBeginIndex, completeEndIndex);
    if (closeElement != null) {
      String after = contents.substring(closeElement.getEndIndex(), completeEndIndex);
      String addition = contents.substring(element.getIndex(), element.getIndex() + element.getMeaningfulLength());
      String replacement = contents.substring(completeBeginIndex, closeElement.getEndIndex()) + addition + after;
      String text = contents.substring(completeBeginIndex, closeElement.getBeginIndex()) + "[[...]]" + addition + after;
      boolean automatic = false;
      if (!automatic) {
        // Mark as automatic if it goes to the end of the main area
        int tmpIndex = closeElement.getEndIndex();
        while ((tmpIndex < element.getMainAreaEnd()) &&
               (" .:;,".indexOf(contents.charAt(tmpIndex)) >= 0)) {
          tmpIndex++;
        }
        automatic |= tmpIndex >= element.getMainAreaEnd();
      }
      if (!automatic) {
        // Mark as automatic if it goes between parenthesis
        if ((element.getIndex() > 0) &&
            (contents.charAt(element.getIndex() - 1) == '(') &&
            (closeElement.getEndIndex() < contents.length()) &&
            (contents.charAt(closeElement.getEndIndex()) == ')')) {
          automatic = true;
        }
      }
      errorResult.addReplacement(replacement, text, automatic);
    }
    errors.add(errorResult);
  }

  /**
   * @param analysis Page analysis.
   * @param element Formatting element.
   * @param skipWhitespace True to skip whitespace after the formatting element.
   * @return Element just after the formatting element.
   */
  private PageElement getElementAfter(
      PageAnalysis analysis,
      PageElementFormatting element,
      boolean skipWhitespace) {
    String contents = analysis.getContents();
    int elementEndIndex = element.getIndex() + element.getLength();
    if (elementEndIndex >= contents.length()) {
      return null;
    }

    // Skip whitespace
    if (skipWhitespace) {
      while ((elementEndIndex < contents.length() - 1) &&
             Character.isWhitespace(contents.charAt(elementEndIndex))) {
        elementEndIndex++;
      }
    }

    // Find a potential element
    char nextChar = contents.charAt(elementEndIndex);
    PageElement potentialCloseElement = null;
    if (nextChar == '[') {
      // Check if it's a link
      potentialCloseElement = analysis.isInInternalLink(elementEndIndex);
      if (potentialCloseElement == null) {
        potentialCloseElement = analysis.isInExternalLink(elementEndIndex);
      }
    } else if (nextChar == '{') {
      // Check if it's a template
      potentialCloseElement = analysis.isInTemplate(elementEndIndex);
    }

    // Check that it can be used
    if ((potentialCloseElement != null) &&
        (potentialCloseElement.getBeginIndex() != elementEndIndex)) {
      potentialCloseElement = null;
    }
    if (potentialCloseElement != null) {
      String subString = contents.substring(
          potentialCloseElement.getBeginIndex(),
          potentialCloseElement.getEndIndex());
      if (subString.contains("\n") || subString.contains("''")) {
        potentialCloseElement = null;
      }
    }

    return potentialCloseElement;
  }

  /**
   * @param analysis Page analysis.
   * @param elements Formatting elements.
   * @param element Formatting element to report.
   * @param errors List of errors.
   * @param cell Table cell.
   * @return True if element has been reported.
   */
  private boolean reportFormattingElementInCellOptions(
      PageAnalysis analysis,
      List<PageElementFormatting> elements,
      PageElementFormatting element,
      Collection<CheckErrorResult> errors,
      PageElementTable.TableCell cell) {

    // Check that the formatting element is alone in the cell options
    String contents = analysis.getContents();
    int beginIndex = element.getIndex();
    while ((beginIndex > cell.getBeginIndex()) &&
           (contents.charAt(beginIndex - 1) == ' ')) {
      beginIndex--;
    }
    while ((beginIndex > cell.getBeginIndex()) &&
           (contents.charAt(beginIndex - 1) == '|')) {
      beginIndex--;
    }
    if (beginIndex != cell.getBeginIndex()) {
      return false;
    }
    int endIndex = element.getIndex() + element.getLength();
    while ((endIndex < cell.getEndOptionsIndex()) &&
           (contents.charAt(endIndex) == ' ')) {
      endIndex++;
    }
    if ((endIndex < cell.getEndOptionsIndex()) &&
        (contents.charAt(endIndex) == '|')) {
      endIndex++;
    }
    if (endIndex != cell.getEndOptionsIndex()) {
      return false;
    }

    // Check other formatting elements in the cell
    PageElementFormattingAnalysis otherFormatting = PageElementFormattingAnalysis.analyzeArea(
        elements, cell.getEndOptionsIndex(), cell.getEndIndex());
    if (otherFormatting.getElements().size() != 1) {
      return false;
    }
    PageElementFormatting otherElement = otherFormatting.getElements().get(0);
    if (otherElement.getLength() != element.getLength()) {
      return false;
    }
    endIndex = otherElement.getIndex() + otherElement.getLength();
    while ((endIndex < cell.getEndIndex()) &&
           (" \n".indexOf(contents.charAt(endIndex)) >= 0)) {
      endIndex++;
    }
    if (endIndex < cell.getEndIndex()) {
      return false;
    }

    // Report error and suggestion
    CheckErrorResult errorResult = createCheckErrorResult(
        analysis, element.getIndex(), cell.getEndOptionsIndex());
    errorResult.addReplacement(
        contents.substring(element.getIndex(), element.getIndex() + element.getLength()),
        true);
    errors.add(errorResult);
    return true;
  }

  /**
   * @param analysis Page analysis.
   * @param elements List of formatting elements.
   * @param element Formatting element to report.
   * @param errors List of errors.
   * @param beginIndex Begin index of the small area around the formatting element.
   * @param endIndex End index of the small area around the formatting element.
   * @param beginArea Begin index of the bigger area around the formatting element.
   * @param endArea End index of the bigger area around the formatting element.
   * @param deleteEmpty True if formatting element can be deleted if the area is empty.
   * @param requiresText True if text is required.
   * @param closeFull True if the formatting element can be closed at the end of the area.
   * @param deleteEnd True if the formatting element can be deleted if at the end.
   * @return True if the error has been reported.
   */
  private boolean reportFormattingElement(
      PageAnalysis analysis,
      List<PageElementFormatting> elements,
      PageElementFormatting element,
      Collection<CheckErrorResult> errors,
      int beginIndex, int endIndex,
      int beginArea, int endArea,
      boolean deleteEmpty, boolean requiresText,
      boolean closeFull, boolean deleteEnd) {

    // Reduce area
    int initialBeginIndex = moveBeginIndex(analysis, beginIndex, endIndex, false);
    beginIndex = moveBeginIndex(analysis, beginIndex, endIndex, true);
    endIndex = moveEndIndex(analysis, beginIndex, endIndex);
    beginArea = moveBeginIndex(analysis, beginArea, endArea, true);
    endArea = moveEndIndex(analysis, beginArea, endArea);

    // Check a few things
    boolean hasSingleQuote = false;
    boolean hasDoubleQuotes = false;
    String contents = analysis.getContents();
    int index = beginIndex;
    while (index < endIndex) {

      int nextIndex = index + 1;

      // Single quotes
      if (contents.charAt(index) == '\'') {
        boolean shouldCount = true;
        while ((nextIndex < endIndex) &&
               (contents.charAt(nextIndex) == '\'')) {
          nextIndex++;
        }
        if ((nextIndex - index == 2) ||
            (nextIndex - index == 3) ||
            (nextIndex - index == 5)) {
          shouldCount = false;
        }
        if (shouldCount) {
          if ((element.isInInternalLink() == null) &&
              (analysis.isInInternalLink(index) != null)) {
            shouldCount = false;
          }
        }
        if (shouldCount && (nextIndex - index == 1)) {
          // Do not count single quotes between 2 letters (punctuation)
          if ((index > 0) &&
              (Character.isLetter(contents.charAt(index - 1))) &&
              (nextIndex < contents.length()) &&
              (Character.isLetter(contents.charAt(nextIndex)))) {
            shouldCount = false;
          }
        }
        if (shouldCount) {
          hasSingleQuote = true;
        }
      }

      // Double quotes
      if ("\"“".indexOf(contents.charAt(index)) >= 0) {
        hasDoubleQuotes = true;
      }

      index = nextIndex;
    }

    // Analyze element after
    PageElement elementAfter = getElementAfter(analysis, element, true);

    // Report with only one formatting element
    PageElementFormattingAnalysis formatting = PageElementFormattingAnalysis.analyzeArea(
        elements, beginIndex, endIndex);
    if (formatting.getElements().size() == 1) {

      // Report with only the formatting element
      if ((element.getIndex() == initialBeginIndex) &&
          (element.getIndex() + element.getLength() == endIndex)) {
        CheckErrorResult errorResult = createCheckErrorResult(
            analysis, beginArea, endArea);
        if (requiresText &&
            ((beginArea == 0) || (contents.charAt(beginIndex - 1) != ' ')) &&
            ((endArea >= contents.length()) || (contents.charAt(endIndex) !=  ' '))) {
          errorResult.addReplacement(" ", deleteEmpty);
        } else {
          errorResult.addReplacement("", deleteEmpty);
        }
        errors.add(errorResult);
        return true;
      }

      // Report with the formatting element at the end
      if (element.getIndex() + element.getLength() == endIndex) {
        if (element.isInSameArea(beginArea)) {
          CheckErrorResult errorResult = createCheckErrorResult(
              analysis, element.getIndex(), element.getIndex() + element.getLength());
          boolean preventDeleteEnd = hasSingleQuote;
          preventDeleteEnd |= hasDoubleQuotes;
          preventDeleteEnd |= !element.isAloneInArea(elements);
          errorResult.addReplacement("", deleteEnd && !preventDeleteEnd);
          errors.add(errorResult);
          return true;
        }
      }

      // Report with the formatting element at the beginning
      if (element.getIndex() == beginIndex) {

        // Check if there's a single element after the formatting element
        boolean hasSingleElement = false;
        boolean hasSingleElementWithoutSpace = true;
        int tmpEndIndex = endIndex;
        while ((tmpEndIndex > beginIndex) &&
               (" .:;,".indexOf(contents.charAt(tmpEndIndex - 1)) >= 0)) {
          tmpEndIndex--;
        }
        if (!hasSingleElement && (elementAfter != null)) {
          if (elementAfter.getEndIndex() == tmpEndIndex) {
            hasSingleElement = true;
            endIndex = tmpEndIndex;
          }
        }
        if (!hasSingleElement) {
          boolean clean = true;
          boolean hasDigit = false;
          boolean hasLetter = false;
          int tmpIndex = element.getIndex() + element.getLength();
          while ((tmpIndex < tmpEndIndex) && clean) {
            char tmpChar = contents.charAt(tmpIndex);
            if ("'’-".indexOf(tmpChar) >= 0) {
              if ((tmpIndex <= 0) ||
                  !Character.isLetter(contents.charAt(tmpIndex - 1))) {
                clean = false;
              }
              if ((tmpIndex + 1 >= endIndex) ||
                  !Character.isLetter(contents.charAt(tmpIndex + 1))) {
                clean = false;
              }
            } else if (Character.isLetter(tmpChar)) {
              hasLetter = true;
            } else if (Character.isDigit(tmpChar)) {
              hasDigit = true;
            } else if (!Character.isWhitespace(tmpChar)) {
              // Do not check elsewhere for punctuation, as it may be a separation between a title and something else
              if ((tmpIndex + 1 < tmpEndIndex) ||
                  (",.!?:;".indexOf(tmpChar) < 0)) {
                clean = false;
              }
            } else {
              hasSingleElementWithoutSpace = false;
            }
            tmpIndex++;
          }
          if (hasDigit && hasLetter) {
            clean = false;
          }
          if (clean) {
            hasSingleElement = true;
          }
        }
        if (hasSingleElement) {
          closeFull = true;
        }

        // Check if something can prevent closing
        boolean preventCloseFull  = hasSingleQuote;
        preventCloseFull |= hasDoubleQuotes;
        preventCloseFull |= (contents.charAt(endIndex - 1) == '\'');
        if (!hasSingleElement || !hasSingleElementWithoutSpace) {
          preventCloseFull |= !element.isAloneInArea(elements);
        } else {
          preventCloseFull |= !element.isAloneInArea(elements, beginArea, endArea);
        }
        if (!element.canBeClosedAt(endIndex)) {
          preventCloseFull = true;
          int tmpIndex = element.getIndex() + element.getLength();
          while ((tmpIndex < endIndex) &&
                 (contents.charAt(tmpIndex) != '\n')) {
            tmpIndex++;
          }
          endIndex = tmpIndex;
        }

        // Close element
        if (element.canBeClosedAt(endIndex)) {
          CheckErrorResult errorResult = createCheckErrorResult(
              analysis, element.getIndex(), endIndex);
          String addition = contents.substring(
              element.getIndex(), element.getIndex() + element.getMeaningfulLength()); 
          String replacement =
              contents.substring(element.getIndex(), endIndex) +
              addition;
          String text = addition + "..." + addition;
            errorResult.addReplacement(replacement, text, closeFull && !preventCloseFull);
          errors.add(errorResult);
          return true;
        }
      }
    }

    return false;
  }

  /**
   * @param analysis Page analysis.
   * @param beginIndex Begin index.
   * @param endIndex End index.
   * @param complete True if complete reduction should be done.
   * @return New begin index with eventually reduced area.
   */
  private int moveBeginIndex(
      PageAnalysis analysis,
      int beginIndex, int endIndex,
      boolean complete) {
    String contents = analysis.getContents();
    boolean tryAgain = false;
    do {
      tryAgain = false;

      // Ignore whitespace at the beginning
      while ((beginIndex < endIndex) &&
             (" \n\t".indexOf(contents.charAt(beginIndex)) >= 0)) {
        beginIndex++;
        tryAgain = true;
      }

      // Ignore templates at the beginning
      if (complete &&
          (beginIndex < endIndex) &&
          (contents.charAt(beginIndex) == '{')) {
        PageElementTemplate template = analysis.isInTemplate(beginIndex);
        if ((template != null) && (template.getBeginIndex() == beginIndex)) {
          if (template.getEndIndex() < endIndex) {
            beginIndex = template.getEndIndex();
            tryAgain = true;
          }
        }
      }

      // Ignore images at the beginning
      if (complete &&
          (beginIndex < endIndex) &&
          (contents.charAt(beginIndex) == '[')) {
        PageElementImage image = analysis.isInImage(beginIndex);
        if ((image != null) && (image.getBeginIndex() == beginIndex)) {
          if (image.getEndIndex() < endIndex) {
            beginIndex = image.getEndIndex();
            tryAgain = true;
          }
        }
      }

      // Ignore unclosed tags at the beginning
      if (complete &&
          (beginIndex < endIndex) &&
          (contents.charAt(beginIndex) == '<')) {
        PageElementTag tag = analysis.isInTag(beginIndex);
        if ((tag != null) && (tag.getBeginIndex() == beginIndex)) {
          if (tag.isFullTag() ||
              !tag.isComplete() ||
              PageElementTag.TAG_HTML_BR.equals(tag.getNormalizedName())) {
            if (tag.getEndIndex() < endIndex) {
              beginIndex = tag.getEndIndex();
              tryAgain = true;
            }
          }
        }
      }

      // Ignore comments at the beginning
      if ((beginIndex < endIndex) && (contents.charAt(beginIndex) == '<')) {
        ContentsComment comment = analysis.comments().getBeginsAt(beginIndex);
        if (comment != null) {
          beginIndex = comment.getEndIndex();
          tryAgain = true;
        }
      }
    } while (tryAgain);
    return beginIndex;
  }

  /**
   * @param analysis Page analysis.
   * @param beginIndex Begin index.
   * @param endIndex End index.
   * @return New end index with eventually reduced area.
   */
  private int moveEndIndex(
      PageAnalysis analysis, int beginIndex, int endIndex) {
    String contents = analysis.getContents();
    boolean tryAgain = false;
    do {
      tryAgain = false;

      // Ignore whitespace at the end
      while ((endIndex > beginIndex) &&
             (" \n\t".indexOf(contents.charAt(endIndex - 1)) >= 0)) {
        endIndex--;
        tryAgain = true;
      }

      // Ignore comments at the end
      if ((endIndex > beginIndex) && (contents.charAt(endIndex - 1) == '>')) {
        ContentsComment comment = analysis.comments().getEndsAt(endIndex);
        if (comment != null) {
          endIndex = comment.getBeginIndex();
          tryAgain = true;
        }
      }

      // Ignore some tags at the end
      if ((endIndex > beginIndex) && (contents.charAt(endIndex - 1) == '>')) {
        PageElementTag tag = analysis.isInTag(endIndex - 1);
        if ((tag != null) && (tag.getEndIndex() == endIndex) && tag.isComplete()) {
          if (PageElementTag.TAG_WIKI_REF.equals(tag.getNormalizedName())) {
            if (tag.getCompleteBeginIndex() > beginIndex) {
              endIndex = tag.getCompleteBeginIndex();
              tryAgain = true;
            }
          }
          else if (PageElementTag.TAG_HTML_BR.equals(tag.getNormalizedName())) {
            if (tag.getBeginIndex() > beginIndex) {
              endIndex = tag.getBeginIndex();
              tryAgain = true;
            }
          }
        }
      }
    } while (tryAgain);
    return endIndex;
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
   * @return List of possible global fixes.
   */
  @Override
  public String[] getGlobalFixes() {
    return globalFixes;
  }

  /**
   * Fix all the errors in the page.
   * 
   * @param fixName Fix name (extracted from getGlobalFixes()).
   * @param analysis Page analysis.
   * @param textPane Text pane.
   * @return Page contents after fix.
   */
  @Override
  public String fix(String fixName, PageAnalysis analysis, MWPane textPane) {
    return fixUsingAutomaticReplacement(analysis);
  }
}
