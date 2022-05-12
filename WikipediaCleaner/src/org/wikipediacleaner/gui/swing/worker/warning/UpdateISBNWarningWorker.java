/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.gui.swing.worker.warning;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.configuration.WPCConfiguration;
import org.wikipediacleaner.api.configuration.WPCConfigurationString;
import org.wikipediacleaner.api.configuration.WPCConfigurationStringList;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.DataManager;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageComparator;
import org.wikipediacleaner.api.data.PageElementISBN;
import org.wikipediacleaner.api.data.PageElementISSN;
import org.wikipediacleaner.api.data.contents.comment.ContentsComment;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.gui.swing.InformationWindow;
import org.wikipediacleaner.gui.swing.basic.BasicWindow;
import org.wikipediacleaner.gui.swing.basic.Utilities;
import org.wikipediacleaner.i18n.GT;
import org.wikipediacleaner.utils.Configuration;
import org.wikipediacleaner.utils.ConfigurationValueString;


/**
 * SwingWorker for updating ISBN warning.
 */
public class UpdateISBNWarningWorker extends UpdateWarningWorker {

  /**
   * @param wiki Wiki.
   * @param window Window.
   * @param simulation True if this is a simulation.
   */
  public UpdateISBNWarningWorker(
      EnumWikipedia wiki, BasicWindow window,
      boolean simulation) {
    super(wiki, window, null, simulation);
  }

  /**
   * @param wiki Wiki.
   * @param window Window.
   * @param pages Pages to analyze.
   * @param contentsAvailable True if contents is already available in pages.
   * @param automaticEdit True if the edit should be considered automatic.
   */
  public UpdateISBNWarningWorker(
      EnumWikipedia wiki, BasicWindow window, List<Page> pages,
      boolean contentsAvailable, boolean automaticEdit) {
    super(wiki, window, pages, contentsAvailable, automaticEdit);
  }

  /* (non-Javadoc)
   * @see org.wikipediacleaner.gui.swing.basic.BasicWorker#construct()
   */
  @Override
  public Object construct() {
    long startTime = System.currentTimeMillis();
    EnumWikipedia wiki = getWikipedia();
    int lastCount = 0;
    WarningStats stats = new WarningStats();
    Map<String, List<String>> errors = null;
    UpdateISBNWarningTools tools = new UpdateISBNWarningTools(wiki, this, true, automaticEdit);
    try {
      if (!useList) {
        listWarningPages(tools);

        // Ask for confirmation
        if (getWindow() != null) {
          int answer = getWindow().displayYesNoWarning(GT._T(
              "Analysis found {0} articles to check for {1} errors.\n" +
              "Do you want to update the warnings ?",
              new Object[] { Integer.valueOf(warningPages.size()).toString(), "ISBN" } ));
          if (answer != JOptionPane.YES_OPTION) {
            return Integer.valueOf(0);
          }
        }

        // Sort the list of articles
        Collections.sort(warningPages, PageComparator.getTitleFirstComparator());
        if (warningPages.isEmpty()) {
          return Integer.valueOf(0);
        }
      }

      // Working with sublists
      tools.setContentsAvailable(contentsAvailable);
      tools.prepareErrorsMap();
      if (simulation) {
        tools.setSimulation(true);
      }
      String lastTitle = null;
      while (!warningPages.isEmpty()) {
        // Creating sublist
        List<Page> sublist = tools.extractSublist(warningPages, 10, false);
        if (sublist.isEmpty()) {
          errors = tools.getErrorsMap();
          displayResult(stats, startTime, errors);
          return Integer.valueOf(stats.getUpdatedPagesCount());
        }

        // Update warning
        boolean finish = false;
        while (!finish) {
          finish = true;
          try {
            lastTitle = sublist.get(sublist.size() - 1).getTitle();
            tools.updateWarning(sublist, null, null, stats);
          } catch (APIException e) {
            if (getWindow() != null) {
              int answer = getWindow().displayYesNoWarning(GT._T(
                  "An error occurred when updating {1} warnings. Do you want to continue ?\n\n" +
                  "Error: {0}",
                  new Object[] { e.getMessage(), "ISBN" } ));
              if (answer != JOptionPane.YES_OPTION) {
                return e;
              }
              finish = false;
            }
          }
          if (shouldStop()) {
            Configuration config = Configuration.getConfiguration();
            config.setString(null, ConfigurationValueString.LAST_ISBN_WARNING, lastTitle);
            displayResult(stats, startTime, null);
            return Integer.valueOf(stats.getUpdatedPagesCount());
          }
        }

        if (stats.getUpdatedPagesCount() > lastCount) {
          lastCount = stats.getUpdatedPagesCount();
          /*if (getWindow() != null) {
            int answer = getWindow().displayYesNoWarning(
                "This feature is currently under development, please check the modification.\n" +
                "Do you want to continue ?");
            if (answer != JOptionPane.YES_OPTION) {
              return Integer.valueOf(lastCount);
            }
          } else {
            return Integer.valueOf(lastCount);
          }*/
        }
      }
      errors = tools.getErrorsMap();
      if (warningPages.isEmpty()) {
        Configuration config = Configuration.getConfiguration();
        config.setString(null, ConfigurationValueString.LAST_ISBN_WARNING, (String) null);
      }
    } catch (APIException e) {
      return e;
    }

    displayResult(stats, startTime, errors);
    return Integer.valueOf(stats.getUpdatedPagesCount());
  }

  /**
   * Generate the list of warning pages.
   * 
   * @param tools Update warning tools.
   * @throws APIException Exception thrown by the API.
   */
  @Override
  protected void listWarningPages(UpdateWarningTools tools) throws APIException {
    Map<String, Page> tmpWarningPages = new HashMap<>();

    // Retrieve talk pages including a warning
    retrieveArticlesWithWarning(
        WPCConfigurationString.ISBN_WARNING_TEMPLATE,
        tmpWarningPages);

    // Retrieve articles in categories for ISBN errors
    retrieveCategoryMembers(
        WPCConfigurationStringList.ISBN_ERRORS_CATEGORIES,
        tmpWarningPages);

    // Retrieve articles in lists for ISBN errors
    retrieveInternalLinks(
        WPCConfigurationStringList.ISBN_ERRORS_LISTS,
        tmpWarningPages);

    // Retrieve articles listed for ISBN errors in Check Wiki
    retrieveCheckWikiPages(70, tmpWarningPages, null); // Incorrect length
    retrieveCheckWikiPages(71, tmpWarningPages, null); // Incorrect X
    retrieveCheckWikiPages(72, tmpWarningPages, null); // Incorrect ISBN-10
    retrieveCheckWikiPages(73, tmpWarningPages, null); // Incorrect ISBN-13

    // Retrieve articles already reported
    retrieveInternalLinks(
        WPCConfigurationString.ISBN_ERRORS_PAGE,
        tmpWarningPages);

    // Fill up the list    
    warningPages.clear();
    warningPages.addAll(tmpWarningPages.values());
    tmpWarningPages.clear();
  }

  /**
   * Display results.
   * 
   * @param stats Statistics.
   * @param startTime Start time.
   * @param errors Errors found.
   */
  private void displayResult(
      WarningStats stats, long startTime,
      Map<String, List<String>> errors) {
    if (useList) {
      return;
    }

    // Errors
    if (errors != null) {

      // Configuration
      EnumWikipedia wiki = getWikipedia();
      List<String[]> issnSearchEngines = wiki.getConfiguration().getStringArrayList(
          WPCConfigurationStringList.ISSN_SEARCH_ENGINES);
      String issnUrl = null;
      if ((issnSearchEngines != null) &&
          !issnSearchEngines.isEmpty()) {
        String[] issnSearchEngine0 = issnSearchEngines.get(0);
        if ((issnSearchEngine0 != null) && (issnSearchEngine0.length > 1)) {
          issnUrl = issnSearchEngine0[1];
        }
      }

      // Compute synthesis
      StringBuilder buffer = new StringBuilder();
      List<String> keys = new ArrayList<>(errors.keySet());
      Collections.sort(keys);
      for (String key : keys) {
        List<String> values = errors.get(key);
        buffer.append("* ");
        if (values != null) {
          buffer.append(values.size());
          buffer.append(" x ");
        }
        buffer.append(WikiTagType.NOWIKI.getOpenTag());
        buffer.append("ISBN ");
        buffer.append(key);
        buffer.append(WikiTagType.NOWIKI.getCloseTag());
        if (issnUrl != null) {
          String clean = key.replaceAll("\\&\\#x3D\\;", "=");
          clean = PageElementISBN.cleanISBN(clean);
          if (clean.length() == 8) {
            if (clean.charAt(7) == PageElementISSN.computeChecksum(clean)) {
              buffer.append(" ([");
              buffer.append(MessageFormat.format(issnUrl, clean));
              buffer.append(" ISSN?])");
            }
          }
        }
        buffer.append(" : ");
        if (values != null) {
          Collections.sort(values);
          int valueNum = 0;
          while (valueNum < values.size()) {
            if (valueNum > 0) {
              buffer.append(", ");
            }
            String value = values.get(valueNum);
            int begin = valueNum;
            while ((valueNum < values.size()) &&
                   (values.get(valueNum).equals(value))) {
              valueNum++;
            }
            if (valueNum > begin + 1) {
              buffer.append(valueNum - begin);
              buffer.append(" x ");
            }
            buffer.append("[[");
            buffer.append(value);
            buffer.append("]]");
          }
        }
        buffer.append("\n");
      }

      // Update synthesis on dedicated page
      WPCConfiguration config = wiki.getConfiguration();
      String pageName = config.getString(WPCConfigurationString.ISBN_ERRORS_PAGE);
      boolean saved = false;
      if ((pageName != null) && (pageName.trim().length() > 0)) {
        boolean updatePage = false;
        if (simulation && (getWindow() != null)) {
          int answer = Utilities.displayYesNoWarning(
              getWindow().getParentComponent(),
              GT._T("Do you want to update {0}?", pageName));
          if (answer == JOptionPane.YES_OPTION) {
            updatePage = true;
          }
        } else {
          updatePage = true;
        }

        if (updatePage) {
          try {
            Page page = DataManager.createSimplePage(wiki, pageName, null, null, null);
            API api = APIFactory.getAPI();
            api.retrieveContents(wiki, Collections.singletonList(page), false, false);
            String contents = page.getContents();
            if (contents != null) {
              int begin = -1;
              int end = -1;
              for (ContentsComment comment : page.getAnalysis(contents, true).comments().getAll()) {
                String value = comment.getComment().trim();
                if ("BOT BEGIN".equals(value)) {
                  if (begin < 0) {
                    begin = comment.getEndIndex();
                  }
                } else if ("BOT END".equals(value)) {
                  end = comment.getBeginIndex();
                }
              }
              if ((begin >= 0) && (end > begin)) {
                StringBuilder newText = new StringBuilder();
                newText.append(contents.substring(0, begin));
                newText.append("\n");
                newText.append(buffer.toString());
                newText.append(contents.substring(end));
                api.updatePage(
                    wiki, page, newText.toString(),
                    config.getString(WPCConfigurationString.ISBN_ERRORS_PAGE_COMMENT),
                    false, true, true, false);
                saved = true;
              }
            }
          } catch (APIException e) {
            if (getWindow() != null) {
              getWindow().displayWarning(
                  "Error updating list of ISBN errors: " + e.getMessage() +
                  " (" + e.getErrorCode() + ")");
            }
          }
        }
      }

      // Display synthesis
      if (!saved && (getWindow() != null)) {
        InformationWindow.createInformationWindow(
            "ISBN", buffer.toString(), false, getWikipedia());
      }
    }

    // Statistics
    displayStats(stats, startTime);
  }
}
