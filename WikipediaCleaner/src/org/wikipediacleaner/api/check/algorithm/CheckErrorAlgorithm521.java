/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.wikipediacleaner.api.algorithm.AlgorithmParameter;
import org.wikipediacleaner.api.algorithm.AlgorithmParameterElement;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.i18n.GT;


/**
 * Algorithm for analyzing error 521 of check wikipedia project.
 * Error 521: Date format in templates
 */
public class CheckErrorAlgorithm521 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm521() {
    super("Date format in templates");
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

    // Search for incorrect formatting
    boolean result = false;
    for (String[] elements : checks) {
      if ((elements != null) && (elements.length > 2)) {
        List<PageElementTemplate> templates = analysis.getTemplates(elements[0]);
        for (PageElementTemplate template : templates) {
          int paramIndex = template.getParameterIndex(elements[1]);
          if (paramIndex >= 0) {
            String value = template.getParameterValue(paramIndex);
            if ((value != null) && (value.trim().length() > 0)) {
              boolean formatOK = false;
              for (int i = 2; i < elements.length; i++) {
                formatOK |= checkFormat(
                    analysis, template.getParameterValueStartIndex(paramIndex),
                    value, elements[i]);
              }
  
              // Report error
              if (!formatOK) {
                if (errors == null) {
                  return true;
                }
                result = true;
                int beginIndex = template.getParameterValueStartIndex(paramIndex);
                int endIndex = (paramIndex + 1 < template.getParameterCount()) ?
                    template.getParameterPipeIndex(paramIndex + 1) : template.getEndIndex() - 2;
                CheckErrorResult errorResult = createCheckErrorResult(
                    analysis, beginIndex, endIndex);
                for (int i = 2; i < elements.length; i++) {
                  errorResult.addText(elements[i]);
                }
                errors.add(errorResult);
              }
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * Check format.
   * 
   * @param analysis Page analysis.
   * @param offset Offset of the value in the page.
   * @param value Parameter value.
   * @param format Expected format.
   * @return True if the value matches the format.
   */
  private boolean checkFormat(
      PageAnalysis analysis, int offset,
      String value, String format) {
    if ((value == null) || (format == null)) {
      return false;
    }
    int valueIndex = 0;
    int formatIndex = 0;
    while (valueIndex < value.length()) {
      char valueChar = value.charAt(valueIndex);
      boolean formatOk = false;

      // If current value position is the beginning of a comment, skip it
      if ((valueChar == '<') &&
          analysis.comments().isAt(offset + valueIndex)) {
        ContentsComment comment = analysis.comments().getAt(offset + valueIndex);
        formatOk = true;
        valueIndex = comment.getEndIndex() - offset;

      // If current value position is the beginning of a reference, skip it
      } else if ((valueChar == '<') &&
                 (analysis.isInTag(offset + valueIndex, WikiTagType.REF) != null)) {
        PageElementTag tag = analysis.isInTag(offset + valueIndex, WikiTagType.REF);
        formatOk = true;
        valueIndex = tag.getCompleteEndIndex() - offset;

      } else if (formatIndex < format.length()) {
        char formatChar = format.charAt(formatIndex);

        // If format string has a quote, it can be for quoted text or really a quote
        if (formatChar == '\'') {
  
          // Two quotes in format string means a single quote
          if ((formatIndex + 1 < format.length()) &&
              (format.charAt(formatIndex + 1) == '\'')) {
            if (formatChar == '\'') {
              formatOk = true;
              formatIndex += 2;
              valueIndex++;
            }
  
          // Quoted text
          } else {
            int tmpIndex = formatIndex + 1;
            while ((tmpIndex < format.length()) &&
                   (format.charAt(tmpIndex) != '\'')) {
              tmpIndex++;
            }
            if (tmpIndex >= format.length()) {
              return false; // Wrong format: missing closing quote
            }
            int length = tmpIndex - formatIndex - 1;
            if ((valueIndex + length <= value.length()) &&
                format.substring(formatIndex + 1, tmpIndex).equals(value.substring(valueIndex, valueIndex + length))) {
              formatOk = true;
              valueIndex += length;
              formatIndex = tmpIndex + 1;
            }
          }
  
        // Templates
        } else if ((formatChar == '{') &&
                   (formatIndex + 1 < format.length()) &&
                   (format.charAt(formatIndex + 1) == '{')) {
  
          // Find template name
          int tmpIndex = formatIndex + 2;
          while ((tmpIndex < format.length()) &&
                 (format.charAt(tmpIndex) != '}')) {
            tmpIndex++;
          }
          if ((tmpIndex >= format.length()) ||
              !format.startsWith("}}", tmpIndex)) {
            return false;
          }
          String templateName = format.substring(formatIndex + 2, tmpIndex).trim();
  
          // Analyze value
          PageElementTemplate template = analysis.isInTemplate(offset + valueIndex);
          if ((template != null) &&
              (template.getBeginIndex() == offset + valueIndex) &&
              (Page.areSameTitle(templateName, template.getTemplateName()))) {
            formatOk = true;
            valueIndex = template.getEndIndex() - offset;
            formatIndex = tmpIndex + 2;
          }
  
        // Formatting characters
        } else if ((formatChar == 'd') ||
                   (formatChar == 'M') ||
                   (formatChar == 'y')) {
  
          // Count the number of consecutive identical formatting characters
          int formatCount = 0;
          int nextFormatIndex = formatIndex;
          while ((nextFormatIndex < format.length()) &&
                 (format.charAt(nextFormatIndex) == formatChar)) {
            formatCount++;
            nextFormatIndex++;
          }
  
          // Count the number of consecutive digits
          int digitCount = 0;
          int nextValueIndex = valueIndex;
          while ((nextValueIndex < value.length()) &&
                 (Character.isDigit(value.charAt(nextValueIndex)))) {
            digitCount++;
            nextValueIndex++;
          }
          int intValue = 0;
          if (digitCount > 0) {
            try {
              intValue = Integer.parseInt(value.substring(valueIndex, nextValueIndex));
            } catch (NumberFormatException e) {
              // Nothing to do
            }
          }
  
          // Depending on the formatting character
          switch (formatChar) {
          case 'd': // Day
            if (formatCount == 1) {
              // Format "d": day number without leading 0
              if ((digitCount != 0) && (digitCount <= 2) && (valueChar != '0')) {
                if ((intValue > 0) && (intValue <= 31)) {
                  formatOk = true;
                }
              }
            } else {
              // Format "dd": day number with leading 0
              if (digitCount == 2) {
                if ((intValue > 0) && (intValue <= 31)) {
                  formatOk = true;
                }
              }
            }
            break;
  
          case 'M': // Month
            if (formatCount == 1) {
              // Format "M": month number without leading 0
              if ((digitCount != 0) && (digitCount <= 2) && (valueChar != '0')) {
                if ((intValue > 0) && (intValue <= 12)) {
                  formatOk = true;
                }
              }
            } else if (formatCount == 2) {
              // Format "MM": month number with leading 0
              if (digitCount == 2) {
                if ((intValue > 0) && (intValue <= 12)) {
                  formatOk = true;
                }
              }
            } else {
              // Format "MMM": month name
              if ((digitCount == 0) && (months != null)) {
                boolean monthFound = false;
                for (String month : months) {
                  if (!monthFound && value.startsWith(month, nextValueIndex)) {
                    nextValueIndex += month.length();
                    monthFound = true;
                  }
                }
                if (monthFound) {
                  formatOk = true;
                }
              }
            }
            break;
  
          case 'y': // Year
            if (intValue <= 9999) {
              if ((formatCount == 1) || (formatCount == 2)) {
                // Format "Y" or "YY": year on 2 digits
                if (digitCount == 2) {
                  formatOk = true;
                }
              } else {
                // Format "YYY": year on as many digits as required
                if ((digitCount != 0) && (valueChar != '0')) {
                  formatOk = true;
                }
              }
            }
            break;
          }
  
          // Move indexes if needed
          if (formatOk) {
            formatIndex = nextFormatIndex;
            valueIndex = nextValueIndex;
          }
  
        // Check for identical text
        } else {
          if (value.charAt(valueIndex) == format.charAt(formatIndex)) {
            formatOk = true;
            valueIndex++;
            formatIndex++;
          }
        }

      // Check for potential white space characters at the end
      } else if (Character.isWhitespace(valueChar)) {
        formatOk = true;
        valueIndex++;
      }

      // Break if nothing found
      if (!formatOk) {
        return false;
      }
    }

    // Check for potential comments or whitespace characters
    while ((valueIndex < value.length()) && Character.isWhitespace(value.charAt(valueIndex))) {
      valueIndex++;
    }
    if ((valueIndex < value.length()) && value.startsWith(ContentsComment.START, valueIndex)) {
      valueIndex += 2;
      while ((valueIndex < value.length() && !value.startsWith(ContentsComment.END, valueIndex))) {
        valueIndex++;
      }
      if (valueIndex < value.length()) {
        valueIndex += 3;
      } else {
        return false;
      }
    }

    if ((valueIndex < value.length()) || (formatIndex < format.length())) {
      return false;
    }
    return true;
  }

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /** Templates to check for date formats */
  private static final String PARAMETER_TEMPLATES = "templates";

  /** Months names */
  private static final String PARAMETER_MONTHS = "months";

  /**
   * Initialize settings for the algorithm.
   * 
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    String tmp = getSpecificProperty(PARAMETER_TEMPLATES, true, true, false);
    checks.clear();
    if (tmp != null) {
      List<String[]> tmpList = WPCConfiguration.convertPropertyToStringArrayList(tmp);
      if (tmpList != null) {
        checks.addAll(tmpList);
      }
    }
    tmp = getSpecificProperty(PARAMETER_MONTHS, true, true, false);
    months.clear();
    if (tmp != null) {
      List<String> tmpList = WPCConfiguration.convertPropertyToStringList(tmp);
      if (tmpList != null) {
        months.addAll(tmpList);
      }
    }
  }

  /** Templates to check */
  private final List<String[]> checks = new ArrayList<>();

  /** Months names */
  private final List<String> months = new ArrayList<>();

  /**
   * Build the list of parameters for this algorithm.
   */
  @Override
  protected void addParameters() {
    super.addParameters();
    addParameter(new AlgorithmParameter(
        PARAMETER_TEMPLATES,
        GT._T("A list of templates and parameters in which format should be checked"),
        new AlgorithmParameterElement[] {
            new AlgorithmParameterElement(
                "template name",
                GT._T("The name of a template in which format should be checked")),
            new AlgorithmParameterElement(
                "parameter name",
                GT._T("The name of a parameter in which format should be checked")),
            new AlgorithmParameterElement(
                "date format",
                GT._T("A correct format for the date"),
                false, true)
        },
        true));
    addParameter(new AlgorithmParameter(
        PARAMETER_MONTHS,
        GT._T("A list of text values for months"),
        new AlgorithmParameterElement(
            "month name",
            GT._T("A text value for a month")),
        true));
  }
}
