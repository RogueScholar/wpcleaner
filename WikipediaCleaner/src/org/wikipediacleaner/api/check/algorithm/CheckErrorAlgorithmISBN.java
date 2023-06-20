/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */


package org.wikipediacleaner.api.check.algorithm;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.check.Actionnable;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.CompositeAction;
import org.wikipediacleaner.api.check.SimpleAction;
import org.wikipediacleaner.api.check.CheckErrorResult.ErrorLevel;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.configuration.WPCConfigurationString;
import org.wikipediacleaner.api.configuration.WPCConfigurationStringList;
import org.wikipediacleaner.api.data.ISBNRange;
import org.wikipediacleaner.api.data.PageElementISBN;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.SearchEngine;
import org.wikipediacleaner.api.data.ISBNRange.ISBNInformation;
import org.wikipediacleaner.api.data.PageElementFunction;
import org.wikipediacleaner.api.data.PageElementTemplate.Parameter;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.ContentsUtil;
import org.wikipediacleaner.api.data.contents.magicword.FunctionMagicWordType;
import org.wikipediacleaner.api.data.contents.magicword.MagicWord;
import org.wikipediacleaner.api.data.contents.template.TemplateBuilder;
import org.wikipediacleaner.gui.swing.action.ActionExternalViewer;
import org.wikipediacleaner.gui.swing.action.ActionMultiple;
import org.wikipediacleaner.i18n.GT;


/**
 * Base class for errors on ISBN numbers.
 */
public abstract class CheckErrorAlgorithmISBN extends CheckErrorAlgorithmBase {

  /**
   * @param name Algorithm name.
   */
  protected CheckErrorAlgorithmISBN(String name) {
    super(name);
  }

  /**
   * @param analysis Page analysis.
   * @param isbn ISBN.
   * @return true if errors should be ignored.
   */
  protected boolean shouldIgnoreError(PageAnalysis analysis, PageElementISBN isbn) {
    if (!isbn.isTemplateParameter()) {
      PageElementFunction function = analysis.isInFunction(isbn.getBeginIndex());
      if (function != null) {
        MagicWord magicWord = analysis.getWikiConfiguration().getMagicWordByType(FunctionMagicWordType.INVOKE);
        if ((magicWord != null) && (magicWord.isPossibleAlias(function.getFunctionName()))) {
          return true;
        }
      }
      return false;
    }

    WPCConfiguration configuration = analysis.getWPCConfiguration();
    String prefix = configuration.getString(WPCConfigurationString.ACCEPT_THIS_AS_WRITTEN_PREFIX);
    String suffix = configuration.getString(WPCConfigurationString.ACCEPT_THIS_AS_WRITTEN_SUFFIX);
    if (StringUtils.isNotEmpty(prefix) && StringUtils.isNotEmpty(suffix)) {
      PageElementTemplate template = analysis.isInTemplate(isbn.getBeginIndex());
      if (template != null) {
        Parameter param = template.getParameterAtIndex(isbn.getBeginIndex());
        if ((param != null) && (param.getValue() != null)) {
          String value = param.getStrippedValue();
          if (value.startsWith(prefix) && value.endsWith(suffix)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * @param analysis Page analysis.
   * @param isbn ISBN.
   * @param checkForComment True to check for a comment after the ISBN.
   * @return Error result.
   */
  protected CheckErrorResult createCheckErrorResult(
      PageAnalysis analysis, PageElementISBN isbn,
      boolean checkForComment) {
    ErrorLevel level = (isbn.isValid() && !isbn.helpRequested()) ?
        ErrorLevel.ERROR : ErrorLevel.WARNING;
    if (checkForComment) {
      String contents = analysis.getContents();
      int index = isbn.getEndIndex();
      while ((index < contents.length()) && (contents.charAt(index) == ' ')) {
        index++;
      }
      if ((index < contents.length()) && (contents.charAt(index) == '<')) {
        if (analysis.comments().isAt(index)) {
          level = ErrorLevel.WARNING;
        }
      }
    }
    CheckErrorResult result = createCheckErrorResult(
        analysis, isbn.getBeginIndex(), isbn.getEndIndex(), level);
    ISBNInformation infos = ISBNRange.getInformation(isbn.getISBN());
    if ((infos != null) && (infos.getTexts() != null)) {
      for (String info : infos.getTexts()) {
        result.addText(info);
      }
    }
    return result;
  }

  private final String[] possibleSplit = {
      "[,/]",
      "[,/ ]"
  };

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param isbn ISBN.
   */
  protected void addSuggestions(
      PageAnalysis analysis, CheckErrorResult errorResult,
      PageElementISBN isbn) {
    if ((analysis == null) || (isbn == null)) {
      return;
    }

    // Split ISBN in several potential ISBN
    List<String> isbnValues = new ArrayList<>();
    if (isbn.isTemplateParameter()) {

      // Basic splits
      for (String split : possibleSplit) {
        isbnValues.clear();
        for (String value : isbn.getISBNNotTrimmed().trim().split(split)) {
          isbnValues.add(value);
        }
        addSuggestions(analysis, errorResult, isbn, isbnValues, isbnValues.size() == 1);
      }

      // Evolved split
      String isbnValue = isbn.getISBNNotTrimmed().trim();
      isbnValues.clear();
      while (isbnValue.length() > 0) {
        // Remove extra characters
        int index = 0;
        while ((index < isbnValue.length()) &&
               (!Character.isDigit(isbnValue.charAt(index)))) {
          index++;
        }

        // Find characters
        if (index > 0) {
          isbnValue=  isbnValue.substring(index);
        }
        index = 0;
        while ((index < isbnValue.length()) &&
               (!Character.isLetter(isbnValue.charAt(index))) &
               (Character.toUpperCase(isbnValue.charAt(index)) != 'X')) {
          index++;
        }
        if (index > 0) {
          isbnValues.add(isbnValue.substring(0, index));
          isbnValue = isbnValue.substring(index);
        }
      }
      addSuggestions(analysis, errorResult, isbn, isbnValues, false);
    } else {
      isbnValues.add(isbn.getISBNNotTrimmed());
      addSuggestions(analysis, errorResult, isbn, isbnValues, true);
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param isbn ISBN.
   * @param isbnValues Broken down ISBN values.
   * @param automatic True if replacement may be automatic.
   */
  private void addSuggestions(
      PageAnalysis analysis, CheckErrorResult errorResult,
      PageElementISBN isbn, List<String> isbnValues,
      boolean automatic) {
    // Remove empty ISBN
    Iterator<String> itValues = isbnValues.iterator();
    while (itValues.hasNext()) {
      String value = itValues.next();
      if ((value == null) || (value.trim().length() == 0)) {
        itValues.remove();
      }
    }

    // Cleanup potential ISBN
    final String extraChars = " ():./";
    for (int numIsbn = 0; numIsbn < isbnValues.size(); numIsbn++) {
      String isbnValue = isbnValues.get(numIsbn);

      // Remove extra characters at the beginning
      while ((isbnValue.length() > 0) &&
             (extraChars.indexOf(isbnValue.charAt(0)) >= 0)) {
        isbnValue = isbnValue.substring(1);
      }

      // Remove ISBN prefix
      if (isbn.isTemplateParameter()) {
        if (isbnValue.toUpperCase().startsWith("ISBN")) {
          isbnValue = isbnValue.substring(4);
        }
      }

      // Remove extra characters at the beginning
      while ((isbnValue.length() > 0) &&
             (extraChars.indexOf(isbnValue.charAt(0)) >= 0)) {
        isbnValue = isbnValue.substring(1);
      }

      // Remove ISBN-10 or ISBN-13 prefix
      String cleanISBN = PageElementISBN.cleanISBN(isbnValue);
      if (((cleanISBN.length() == 12) && (cleanISBN.startsWith("10"))) ||
          ((cleanISBN.length() == 15) && (cleanISBN.startsWith("13")))) {
        int digitCount = 0;
        int index = 0;
        boolean ok = true;
        while ((index < isbnValue.length()) && (digitCount < 3)) {
          char current = isbnValue.charAt(index);
          if (Character.isDigit(current)) {
            digitCount++;
          } else if (Character.isLetter(current)) {
            ok = false;
          }
          if (digitCount < 3) {
            index++;
          }
        }
        if (ok) {
          isbnValue = isbnValue.substring(index);
        }
      }

      // Handle lower case X at the end
      cleanISBN = PageElementISBN.cleanISBN(isbnValue);
      if ((cleanISBN.length() == 10) && isbnValue.endsWith("x")) {
        isbnValue = isbnValue.replace('x', 'X');
      }

      // Remove extra characters at both extremities
      while ((isbnValue.length() > 0) &&
             (extraChars.indexOf(isbnValue.charAt(0)) >= 0)) {
        isbnValue = isbnValue.substring(1);
      }
      while ((isbnValue.length() > 0) &&
             (extraChars.indexOf(isbnValue.charAt(isbnValue.length() - 1)) >= 0)) {
        isbnValue = isbnValue.substring(0, isbnValue.length() - 1);
      }
      isbnValues.set(numIsbn, isbnValue);
    }

    // Remove empty ISBN
    itValues = isbnValues.iterator();
    while (itValues.hasNext()) {
      String value = itValues.next();
      if ((value == null) || (value.trim().length() == 0)) {
        itValues.remove();
      }
    }
    if (isbnValues.isEmpty()) {
      return;
    }

    // Check if all potential ISBN are valid
    for (String isbnValue : isbnValues) {
      String cleanISBN = PageElementISBN.cleanISBN(isbnValue);
      int length = cleanISBN.length();
      if ((length != 10) && (length != 13)) {
        return;
      }
    }

    // Suggestions with only one ISBN
    if (isbnValues.size() == 1) {
      String value = isbnValues.get(0);
      if (value.equals(isbn.getISBNNotTrimmed())) {
        return;
      }
      if (isbn.isTemplateParameter()) {
        automatic &= PageElementISBN.isValid(value);
        automatic &= ContentsUtil.moveIndexForwardWhileFound(value, 0, PageElementISBN.POSSIBLE_CHARACTERS + PageElementISBN.EXTRA_CHARACTERS) == value.length();
        errorResult.addReplacement(value, automatic);
      } else {
        errorResult.addReplacement(PageElementISBN.ISBN_PREFIX + " " + value);
      }
      return;
    }

    // Suggestions with several ISBN
    if (isbn.isTemplateParameter()) {
      PageElementTemplate template = analysis.isInTemplate(isbn.getBeginIndex());
      if (template != null) {
        Parameter param = template.getParameterAtIndex(isbn.getBeginIndex());
        if ((param != null) &&
            (param.getName() != null) &&
            (param.getName().trim().length() > 0)) {
          String name = param.getName().trim();
          int index = name.length();
          while ((index > 0) &&
                 (Character.isDigit(name.charAt(index - 1)))) {
            index--;
          }
          int currentNum = 1;
          if (index < name.length()) {
            currentNum = Integer.valueOf(name.substring(index));
            name = name.substring(0, index);
          }
          currentNum++;
          StringBuilder buffer = new StringBuilder();
          buffer.append(isbnValues.get(0));
          for (int isbnNum = 1; isbnNum < isbnValues.size(); isbnNum++) {
            while (template.getParameterIndex(name + Integer.toString(currentNum)) >= 0) {
              currentNum++;
            }
            buffer.append(" |");
            buffer.append(name);
            buffer.append(Integer.toString(currentNum));
            buffer.append("=");
            buffer.append(isbnValues.get(isbnNum));
            currentNum++;
          }
          errorResult.addReplacement(buffer.toString());
        }
      }
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param isbn ISBN.
   */
  protected void addHelpNeededTemplates(
      PageAnalysis analysis, CheckErrorResult errorResult,
      PageElementISBN isbn) {
    WPCConfiguration config = analysis.getWPCConfiguration();
    List<String[]> helpNeededTemplates = config.getStringArrayList(
        WPCConfigurationStringList.ISBN_HELP_NEEDED_TEMPLATES);
    if ((helpNeededTemplates != null) &&
        (!helpNeededTemplates.isEmpty())) {
      String reason = getReason(isbn);
      for (String[] helpNeededTemplate : helpNeededTemplates) {
        String replacement = isbn.askForHelp(helpNeededTemplate, reason);
        if (replacement != null) {
          errorResult.addReplacement(
              replacement.toString(),
              GT._T("Ask for help using {0}", TemplateBuilder.from(helpNeededTemplate[0]).toString()));
        }
      }
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param isbn ISBN.
   */
  protected void addHelpNeededComment(
      PageAnalysis analysis, CheckErrorResult errorResult,
      PageElementISBN isbn) {
    WPCConfiguration config = analysis.getWPCConfiguration();
    String helpNeededComment = config.getString(
        WPCConfigurationString.ISBN_HELP_NEEDED_COMMENT);
    if (helpNeededComment != null) {
      String reason = getReason(isbn);
      String replacement = isbn.askForHelp(helpNeededComment, reason);
      if (replacement != null) {
        String contents = analysis.getContents();
        replacement =
            contents.substring(isbn.getBeginIndex(), isbn.getEndIndex()) +
            replacement;
        errorResult.addReplacement(replacement, GT._T("Add a comment"));
      }
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param searches List of strings to search.
   * @param title Title for the group of searches.
   */
  protected void addSearchEngines(
      PageAnalysis analysis, CheckErrorResult errorResult,
      List<String> searches, String title) {

    // Check configuration
    if ((searches == null) || searches.isEmpty()) {
      return;
    }
    WPCConfiguration config = analysis.getWPCConfiguration();
    List<String[]> searchEngines = config.getStringArrayList(
        WPCConfigurationStringList.ISBN_SEARCH_ENGINES);
    if ((searchEngines == null) || searchEngines.isEmpty()) {
      return;
    }

    // Add title
    if ((title != null) && (searches.size() > 1)) {
      errorResult.addPossibleAction(new SimpleAction(title, null));
    }

    // Create global actions
    addGlobalSearchEngines(
        analysis, errorResult,
        searches,
        GT._T("Search all ISBN"));
    addGlobalSearchEngines(
        analysis,
        errorResult,
        searches.stream().filter(t -> t.length() == 10).collect(Collectors.toList()),
        GT._T("Search all ISBN-10"));
    addGlobalSearchEngines(
        analysis,
        errorResult,
        searches.stream().filter(t -> t.length() == 13).collect(Collectors.toList()),
        GT._T("Search all ISBN-13"));

    // Create unit actions
    for (String search : searches) {
      List<Actionnable> actions = new ArrayList<>();
      for (String[] searchEngine : searchEngines) {
        try {
          if (searchEngine.length > 1) {
            actions.add(new SimpleAction(
                searchEngine[0],
                new ActionExternalViewer(MessageFormat.format(searchEngine[1], search))));
          }
        } catch (IllegalArgumentException e) {
          //
        }
      }
      errorResult.addPossibleAction(new CompositeAction(
          GT._T("Search ISBN {0}", search), actions));
    }
  }

  private void addGlobalSearchEngines(
      PageAnalysis analysis, CheckErrorResult errorResult,
      List<String> searches, String title) {
    if (searches.size() <= 1) {
      return;
    }
    WPCConfiguration config = analysis.getWPCConfiguration();
    List<String[]> searchEngines = config.getStringArrayList(
        WPCConfigurationStringList.ISBN_SEARCH_ENGINES);
    List<Actionnable> actions = new ArrayList<>();
    for (String[] searchEngine : searchEngines) {
      if (searchEngine.length > 1) {
        ActionMultiple action = new ActionMultiple();
        for (String search : searches) {
          try {
            action.addAction(
                new ActionExternalViewer(MessageFormat.format(searchEngine[1], search)));
          } catch (IllegalArgumentException e) {
            //
          }
        }
        actions.add(new SimpleAction(searchEngine[0], action));
      }
    }
    errorResult.addPossibleAction(new CompositeAction(title, actions));
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param search String to search.
   */
  protected void addSearchEngines(
      PageAnalysis analysis, CheckErrorResult errorResult,
      String search) {
    WPCConfiguration config = analysis.getWPCConfiguration();
    List<String[]> searchEngines = config.getStringArrayList(
        WPCConfigurationStringList.ISBN_SEARCH_ENGINES);
    if ((searchEngines != null) &&
        (!searchEngines.isEmpty())) {
      List<Actionnable> actions = new ArrayList<>();
      for (String[] searchEngine : searchEngines) {
        try {
          if (searchEngine.length > 1) {
            actions.add(new SimpleAction(
                searchEngine[0],
                new ActionExternalViewer(MessageFormat.format(searchEngine[1], search))));
          }
        } catch (IllegalArgumentException e) {
          //
        }
      }
      errorResult.addPossibleAction(new CompositeAction(
          GT._T("Search ISBN {0}", search), actions));
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param search String to search.
   */
  protected void addSearchEnginesISSN(
      PageAnalysis analysis, CheckErrorResult errorResult,
      String search) {
    WPCConfiguration config = analysis.getWPCConfiguration();
    List<String[]> searchEngines = config.getStringArrayList(
        WPCConfigurationStringList.ISSN_SEARCH_ENGINES);
    if ((searchEngines != null) &&
        (!searchEngines.isEmpty())) {
      List<Actionnable> actions = new ArrayList<>();
      for (String[] searchEngine : searchEngines) {
        try {
          if (searchEngine.length > 1) {
            actions.add(new SimpleAction(
                searchEngine[0],
                new ActionExternalViewer(MessageFormat.format(searchEngine[1], search))));
          }
        } catch (IllegalArgumentException e) {
          //
        }
      }
      errorResult.addPossibleAction(new CompositeAction(
          GT._T("Search ISSN {0}", search), actions));
    }
  }

  /**
   * @param analysis Page analysis.
   * @param errorResult Error result.
   * @param template Template in which the ISBN is.
   */
  protected void addSearchEngines(
      PageAnalysis analysis, CheckErrorResult errorResult,
      PageElementTemplate template) {
    if (template == null) {
      return;
    }

    // Add search engines
    Map<String, List<SearchEngine>> searchEngines = SearchEngine.getSearchEngines(
        analysis.getWikipedia(), template,
        WPCConfigurationStringList.ISBN_SEARCH_ENGINES_TEMPLATES);
    if (searchEngines == null) {
      return;
    }
    List<String> parameterNames = new ArrayList<>(searchEngines.keySet());
    Collections.sort(parameterNames);
    for (String parameterName : parameterNames) {
      List<Actionnable> actions = new ArrayList<>();
      for (SearchEngine searchEngine : searchEngines.get(parameterName)) {
        actions.add(new SimpleAction(
            searchEngine.getName(),
            new ActionExternalViewer(searchEngine.getUrl())));
      }
      errorResult.addPossibleAction(new CompositeAction(
          GT._T("Search using {0}", parameterName), actions));
    }
  }

  /**
   * @param isbn ISBN number.
   * @return Reason for the error.
   */
  public abstract String getReason(PageElementISBN isbn);
}
