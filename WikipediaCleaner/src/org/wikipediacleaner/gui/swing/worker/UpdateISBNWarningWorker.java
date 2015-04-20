/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.gui.swing.worker;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.constants.WPCConfiguration;
import org.wikipediacleaner.api.constants.WPCConfigurationString;
import org.wikipediacleaner.api.constants.WPCConfigurationStringList;
import org.wikipediacleaner.api.constants.WikiConfiguration;
import org.wikipediacleaner.api.data.DataManager;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageComparator;
import org.wikipediacleaner.api.data.PageElementComment;
import org.wikipediacleaner.api.data.PageElementISBN;
import org.wikipediacleaner.gui.swing.InformationWindow;
import org.wikipediacleaner.gui.swing.basic.BasicWindow;
import org.wikipediacleaner.gui.swing.basic.Utilities;
import org.wikipediacleaner.gui.swing.worker.UpdateWarningTools.Stats;
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
    super(wiki, window, simulation);
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
    Stats stats = new Stats();
    Map<String, List<String>> errors = null;
    try {
      if (!useList) {
        listWarningPages();

        // Ask for confirmation
        if (getWindow() != null) {
          int answer = getWindow().displayYesNoWarning(GT._(
              "Analysis found {0} articles to check for ISBN errors.\n" +
              "Do you want to update the warnings ?",
              Integer.valueOf(warningPages.size()).toString() ));
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
      UpdateISBNWarningTools tools = new UpdateISBNWarningTools(wiki, this, true, automaticEdit);
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
            tools.updateWarning(sublist, null, null, stats);
            lastTitle = sublist.get(sublist.size() - 1).getTitle();
          } catch (APIException e) {
            if (getWindow() != null) {
              int answer = getWindow().displayYesNoWarning(GT._(
                  "An error occurred when updating ISBN warnings. Do you want to continue ?\n\n" +
                  "Error: {0}", e.getMessage()));
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
   */
  @Override
  protected void listWarningPages() throws APIException {
    EnumWikipedia wiki = getWikipedia();
    WPCConfiguration configuration = wiki.getConfiguration();
    WikiConfiguration wikiConfiguration = wiki.getWikiConfiguration();
    API api = APIFactory.getAPI();

    // Retrieve talk pages including a warning
    String warningTemplateName = configuration.getString(WPCConfigurationString.ISBN_WARNING_TEMPLATE);
    if (warningTemplateName != null) {
      setText(GT._("Retrieving talk pages including {0}", "{{" + warningTemplateName + "}}"));
      String templateTitle = wikiConfiguration.getPageTitle(
          Namespace.TEMPLATE,
          warningTemplateName);
      Page warningTemplate = DataManager.getPage(
          wiki, templateTitle, null, null, null);
      api.retrieveEmbeddedIn(
          wiki, warningTemplate,
          configuration.getEncyclopedicTalkNamespaces(),
          false);
      warningPages.addAll(warningTemplate.getRelatedPages(Page.RelatedPages.EMBEDDED_IN));
    }

    // Retrieve articles in categories for ISBN errors
    List<String> categories = configuration.getStringList(WPCConfigurationStringList.ISBN_ERRORS_CATEGORIES);
    if (categories != null) {
      for (String category : categories) {
        String categoryTitle = wikiConfiguration.getPageTitle(Namespace.CATEGORY, category);
        Page categoryPage = DataManager.getPage(wiki, categoryTitle, null, null, null);
        api.retrieveCategoryMembers(wiki, categoryPage, 0, false, Integer.MAX_VALUE);
        List<Page> categoryMembers = categoryPage.getRelatedPages(
            Page.RelatedPages.CATEGORY_MEMBERS);
        if (categoryMembers != null) {
          warningPages.addAll(categoryMembers);
        }
      }
    }

    // Retrieve articles listed for ISBN errors in Check Wiki
    retrieveCheckWikiPages(70, warningPages); // Incorrect length
    retrieveCheckWikiPages(71, warningPages); // Incorrect X
    retrieveCheckWikiPages(72, warningPages); // Incorrect ISBN-10
    retrieveCheckWikiPages(73, warningPages); // Incorrect ISBN-13

    // Retrieve articles already reported
    String isbnErrorsPageName = configuration.getString(WPCConfigurationString.ISBN_ERRORS_PAGE);
    if (isbnErrorsPageName != null) {
      Page page = DataManager.getPage(wiki, isbnErrorsPageName, null, null, null);
      api.retrieveLinks(wiki, page, Namespace.MAIN, null, false, false);
      List<Page> links = page.getLinks();
      if (links != null) {
        warningPages.addAll(links);
      }
    }

    // Construct list of articles with warning
    setText(GT._("Constructing list of articles with warning"));
    HashSet<Page> tmpWarningPages = new HashSet<Page>();
    List<Integer> encyclopedicNamespaces = configuration.getEncyclopedicNamespaces();
    for (Page warningPage : warningPages) {

      // Get article page for talks pages and to do sub-pages
      String title = warningPage.getTitle();
      if (!warningPage.isArticle()) {
        String todoSubpage = configuration.getString(WPCConfigurationString.TODO_SUBPAGE);
        if (title.endsWith("/" + todoSubpage)) {
          title = title.substring(0, title.length() - 1 - todoSubpage.length());
        }
        Integer namespace = warningPage.getNamespace();
        if (namespace != null) {
          Namespace namespaceTalk = wikiConfiguration.getNamespace(namespace.intValue());
          if (namespaceTalk != null) {
            int colonIndex = title.indexOf(':');
            if (colonIndex >= 0) {
              title = title.substring(colonIndex + 1);
            }
            if (namespace != Namespace.MAIN_TALK) {
              title = wikiConfiguration.getPageTitle(namespace - 1, title);
            }
          }
        }
      }

      // Add article to the list
      Page page = DataManager.getPage(wiki, title, null, null, null);
      if (encyclopedicNamespaces.contains(page.getNamespace()) &&
          !tmpWarningPages.contains(page)) {
        tmpWarningPages.add(page);
      }
    }

    // Fill up the list    
    warningPages.clear();
    warningPages.addAll(tmpWarningPages);
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
      Stats stats, long startTime,
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
      List<String> keys = new ArrayList<String>(errors.keySet());
      Collections.sort(keys);
      for (String key : keys) {
        List<String> values = errors.get(key);
        buffer.append("* ");
        if (values != null) {
          buffer.append(values.size());
          buffer.append(" x ");
        }
        buffer.append("ISBN ");
        buffer.append(key);
        if (issnUrl != null) {
          String clean = key.replaceAll("\\&\\#x3D\\;", "=");
          clean = PageElementISBN.cleanISBN(clean);
          if (clean.length() == 8) {
            buffer.append(" ([");
            buffer.append(MessageFormat.format(issnUrl, clean));
            buffer.append(" ISSN?])");
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
              GT._("Do you want to update {0}?", pageName));
          if (answer == JOptionPane.YES_OPTION) {
            updatePage = true;
          }
        } else {
          updatePage = true;
        }

        if (updatePage) {
          try {
            Page page = DataManager.getPage(wiki, pageName, null, null, null);
            API api = APIFactory.getAPI();
            api.retrieveContents(wiki, Collections.singletonList(page), false, false);
            String contents = page.getContents();
            if (contents != null) {
              int begin = -1;
              int end = -1;
              for (PageElementComment comment : page.getAnalysis(contents, true).getComments()) {
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
                    wiki.formatComment(
                        config.getString(WPCConfigurationString.ISBN_ERRORS_PAGE_COMMENT),
                        true),
                    false);
                saved = true;
              }
            }
          } catch (APIException e) {
            // Nothing
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
