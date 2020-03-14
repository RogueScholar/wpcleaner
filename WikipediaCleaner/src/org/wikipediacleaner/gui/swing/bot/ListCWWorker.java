/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.gui.swing.bot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.MediaWikiController;
import org.wikipediacleaner.api.MediaWikiListener;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.CheckErrorResult.ErrorLevel;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithm;
import org.wikipediacleaner.api.constants.EnumQueryResult;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.DataManager;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageAnalysis;
import org.wikipediacleaner.api.data.PageElementInternalLink;
import org.wikipediacleaner.api.data.contents.ContentsComment;
import org.wikipediacleaner.api.dump.DumpProcessor;
import org.wikipediacleaner.api.dump.PageProcessor;
import org.wikipediacleaner.api.execution.MediaWikiCallable;
import org.wikipediacleaner.gui.swing.basic.BasicWindow;
import org.wikipediacleaner.gui.swing.basic.BasicWorker;
import org.wikipediacleaner.gui.swing.basic.Utilities;
import org.wikipediacleaner.i18n.GT;

/**
 * SwingWorker for listing Check Wiki errors from a dump.
 */
public class ListCWWorker extends BasicWorker {

  /** Logger */
  private final static Logger log = LoggerFactory.getLogger(ListCWWorker.class);

  /** Logger CW */
  final static Logger logCW = LoggerFactory.getLogger("DumpAnalysis");

  /** File containing the dump */
  private final File dumpFile;

  /**
   * Directory (or file with place holder for error number) in which the output
   * is written
   */
  private final File output;

  /**
   * Page name (with place holder for error number) in which the output is
   * written
   */
  private final String pageName;

  /** Algorithms for which to analyze pages */
  final List<AlgorithmInformation> selectedAlgorithms;

  /** True if article should be checked on wiki */
  final boolean checkWiki;

  /** True to just check the pages that have been previously reported */
  final boolean onlyRecheck;

  /** Time spent in analysis. */
  PageAnalysis.AnalysisPerformance analysisTime;

  /** Count of pages analyzed */
  int countAnalyzed;

  /** Count of pages found with errors */
  int countDetections;

  /**
   * @param wiki Wiki.
   * @param window Window.
   * @param dumpFile File containing the dump to be analyzed.
   * @param output Directory (or file with place holder for error number) in
   *     which the output is written.
   * @param selectedAlgorithms List of selected algorithms.
   * @param checkWiki True if last version of articles should be checked on
   *     wiki.
   */
  public ListCWWorker(EnumWikipedia wiki, BasicWindow window, File dumpFile,
                      File output, List<CheckErrorAlgorithm> selectedAlgorithms,
                      boolean checkWiki) {
    super(wiki, window);
    this.dumpFile = dumpFile;
    this.output = output;
    this.pageName = null;
    this.selectedAlgorithms =
        AlgorithmInformation.createList(selectedAlgorithms);
    this.analysisTime = new PageAnalysis.AnalysisPerformance();
    this.countAnalyzed = 0;
    this.countDetections = 0;
    this.checkWiki = checkWiki;
    this.onlyRecheck = false;
  }

  /**
   * @param wiki Wiki.
   * @param window Window.
   * @param dumpFile File containing the dump to be analyzed.
   * @param pageName Page name (with place holder for error number) in which the
   *     output is written.
   * @param selectedAlgorithms List of selected algorithms.
   * @param checkWiki True if last version of articles should be checked on
   *     wiki.
   * @param onlyRecheck True to just check the pages that have been previously
   *     reported.
   */
  public ListCWWorker(EnumWikipedia wiki, BasicWindow window, File dumpFile,
                      String pageName,
                      List<CheckErrorAlgorithm> selectedAlgorithms,
                      boolean checkWiki, boolean onlyRecheck) {
    super(wiki, window);
    this.dumpFile = dumpFile;
    this.output = null;
    this.pageName = pageName;
    this.selectedAlgorithms =
        AlgorithmInformation.createList(selectedAlgorithms);
    this.analysisTime = new PageAnalysis.AnalysisPerformance();
    this.countAnalyzed = 0;
    this.countDetections = 0;
    this.checkWiki = checkWiki;
    this.onlyRecheck = onlyRecheck;
  }

  /**
   * Compute the value to be returned by the <code>get</code> method.
   *
   * @return Object returned by the <code>get</code> method.
   * @see org.wikipediacleaner.gui.swing.basic.BasicWorker#construct()
   */
  @Override
  public Object construct() {
    if ((dumpFile == null) || !dumpFile.canRead() || !dumpFile.isFile()) {
      return null;
    }
    if ((output == null) && (pageName == null)) {
      return null;
    }
    if (output != null) {
      if (!output.canWrite()) {
        return null;
      }
      if (!output.getName().contains("{0}") && !output.isDirectory()) {
        return null;
      }
    }
    if ((selectedAlgorithms == null) || selectedAlgorithms.isEmpty()) {
      return null;
    }
    CWPageProcessor pageProcessor = new CWPageProcessor(getWikipedia(), this);
    if (onlyRecheck) {
      try {
        List<Page> outputPages = new ArrayList<>();
        for (AlgorithmInformation algorithm : selectedAlgorithms) {
          String truePageName = MessageFormat.format(
              pageName, algorithm.algorithm.getErrorNumberString());
          Page page = DataManager.getPage(getWikipedia(), truePageName, null,
                                          null, null);
          outputPages.add(page);
        }
        API api = APIFactory.getAPI();
        api.retrieveLinks(getWikipedia(), outputPages);
        for (Page page : outputPages) {
          List<Page> links = page.getLinks();
          if (links != null) {
            for (Page link : links) {
              pageProcessor.addPage(link);
            }
          }
        }
      } catch (APIException e) {
        // Nothing to do
      }
    }
    DumpProcessor dumpProcessor = new DumpProcessor(pageProcessor);
    dumpProcessor.processDump(dumpFile);
    while (!pageProcessor.hasFinished()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // Nothing to do
      }
    }
    logCW.info("Beginning of result output");
    for (AlgorithmInformation algorithm : selectedAlgorithms) {
      Map<String, Detection> pages = algorithm.getDetections();
      if (pages == null) {
        pages = new HashMap<>();
      }
      outputResult(algorithm.algorithm, pages.values());
    }
    logCW.info("End of result output");
    reportProgress();

    return null;
  }

  /**
   * Report progress.
   */
  void reportProgress() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("\n");
    buffer.append("Pages processed: " + countAnalyzed);
    buffer.append(" / errors detected: " + countDetections);
    buffer.append(" Analysis: " + analysisTime.toString());
    for (AlgorithmInformation algorithm : selectedAlgorithms) {
      buffer.append(" Algorithm " + algorithm.algorithm.getErrorNumberString() +
                    ": " + (algorithm.getTimeSpent() / 1000000000));
    }
    log.info(buffer.toString());
  }

  /**
   * @param pages List of detections.
   * @return Formatted result.
   */
  private String generateResult(List<Detection> pages, Long maxSize) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<!-- Generated using ");
    buffer.append(dumpFile.getName());
    buffer.append(" -->\n");
    ErrorLevel lastLevel = null;
    StringBuilder line = new StringBuilder();
    List<Detection> pagesToRemove = new ArrayList<>();
    for (Detection detection : pages) {
      line.setLength(0);
      if ((detection.maxLevel != null) &&
          !detection.maxLevel.equals(lastLevel)) {
        lastLevel = detection.maxLevel;
        line.append("<!-- ");
        line.append(lastLevel.toString());
        line.append(" -->\n");
      }
      line.append("* ");
      line.append(
          PageElementInternalLink.createInternalLink(detection.pageName, null));
      if (detection.notices != null) {
        boolean first = true;
        for (String notice : detection.notices) {
          line.append(first ? ": " : ", ");
          first = false;
          line.append("<nowiki>");
          int index = 0;
          while (index < notice.length()) {
            int codePoint = notice.codePointAt(index);
            switch (codePoint) {
            case '&': // Replace "&" by its HTML element
              line.append("&amp;");
              break;
            case '\n': // Replace \n by a visual character
              line.append('\u21b5');
              break;
            case '<': // Replace "<" by its HTML element
              line.append("&lt;");
              break;
            case '\u007F': // Replace control characters by visible text
              line.append("[DEL]");
              break;
            case '\u00A0': // Replace control characters by visible text
              line.append("[NBSP]");
              break;
            case '\u00AD': // Replace control characters by visible text
              line.append("[SHY]");
              break;
            case '\u2004': // Replace control characters by visible text
              line.append("[3EM]");
              break;
            case '\u2005': // Replace control characters by visible text
              line.append("[4EM]");
              break;
            case '\u2006': // Replace control characters by visible text
              line.append("[6EM]");
              break;
            case '\u2007': // Replace control characters by visible text
              line.append("[FS]");
              break;
            case '\u2008': // Replace control characters by visible text
              line.append("[PS]");
              break;
            case '\u200B': // Replace control characters by visible text
              line.append("[0WS]");
              break;
            case '\u200E': // Replace control characters by visible text
              line.append("[LRM]");
              break;
            case '\u200F': // Replace control characters by visible text
              line.append("[RLM]");
              break;
            case '\u2028': // Replace control characters by visible text
              line.append("[LS]");
              break;
            case '\u202A': // Replace control characters by visible text
              line.append("[LRE]");
              break;
            case '\u202B': // Replace control characters by visible text
              line.append("[RLE]");
              break;
            case '\u202C': // Replace control characters by visible text
              line.append("[POPD]");
              break;
            case '\u202D': // Replace control characters by visible text
              line.append("[LRO]");
              break;
            case '\u202E': // Replace control characters by visible text
              line.append("[RLO]");
              break;
            case '\uFEFF': // Replace control characters by visible text
              line.append("[BOM]");
              break;
            case '\uFFFC': // Replace control characters by visible text
              line.append("[ORC]");
              break;
            default:
              if ((codePoint >= 0xE000) && (codePoint <= 0xF8FF)) {
                line.append("[PUA]");
              } else if ((codePoint >= 0XF0000) && (codePoint <= 0xFFFFD)) {
                line.append("[PUA_A]");
              } else if ((codePoint >= 0x100000) && (codePoint <= 0x10FFFD)) {
                line.append("[PUA_B]");
              } else {
                line.appendCodePoint(codePoint);
              }
            }
            index = notice.offsetByCodePoints(index, 1);
          }
          line.append("</nowiki>");
        }
      }
      line.append("\n");
      if ((maxSize == null) || (buffer.length() + line.length() < maxSize)) {
        buffer.append(line);
      } else {
        pagesToRemove.add(detection);
      }
    }
    pages.removeAll(pagesToRemove);
    return buffer.toString();
  }

  /**
   * Output result of the analysis.
   *
   * @param algorithm Algorithm.
   * @param pages List of pages with detections.
   */
  private void outputResult(CheckErrorAlgorithm algorithm,
                            Collection<Detection> pages) {
    if ((algorithm == null) || (pages == null)) {
      return;
    }

    // Prepare result
    logCW.info("Preparing results of dump analysis for error " +
               algorithm.getErrorNumberString());
    List<Detection> tmpPages = new ArrayList<>(pages);
    Collections.sort(tmpPages);
    int nbPages = tmpPages.size();
    String result = generateResult(tmpPages, null);

    // Output to file
    if (output != null) {
      File outputFile = null;
      if (!output.getName().contains("{0}")) {
        outputFile = new File(
            output, "CW_" + getWikipedia().getSettings().getCodeCheckWiki() +
                        "_" + algorithm.getErrorNumberString() + ".txt");
      } else {
        outputFile = new File(MessageFormat.format(
            output.getAbsolutePath(), algorithm.getErrorNumberString()));
      }
      logCW.info("Writing dump analysis results for error " +
                 algorithm.getErrorNumberString() + " to file " +
                 outputFile.getName());
      BufferedWriter writer = null;
      try {
        writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(outputFile, false), "UTF8"));
        writer.write(result);
      } catch (IOException e) {
        // Nothing to do
      } finally {
        if (writer != null) {
          try {
            writer.close();
          } catch (IOException e) {
            // Nothing to do
          }
        }
      }
    }

    // Output to a page
    if (pageName != null) {
      String truePageName =
          MessageFormat.format(pageName, algorithm.getErrorNumberString());
      logCW.info("Writing dump analysis results for error " +
                 algorithm.getErrorNumberString() + " to page " + truePageName);
      boolean finished = false;
      while (!finished) {
        try {
          finished = true;
          Page page = DataManager.getPage(getWikipedia(), truePageName, null,
                                          null, null);
          API api = APIFactory.getAPI();
          api.retrieveContents(getWikipedia(), Collections.singletonList(page),
                               false, false);
          String contents = page.getContents();
          if (contents != null) {
            int begin = -1;
            int end = -1;
            for (ContentsComment comment :
                 page.getAnalysis(contents, true).getComments()) {
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
              String text = null;
              finished = false;
              while (!finished) {
                StringBuilder newText = new StringBuilder();
                newText.append(contents.substring(0, begin));
                newText.append("\n");
                newText.append(result);
                newText.append(contents.substring(end));
                text = newText.toString();
                if (!getWikipedia().getWikiConfiguration().isArticleTooLong(
                        text) ||
                    tmpPages.isEmpty()) {
                  finished = true;
                } else {
                  tmpPages.remove(tmpPages.size() - 1);
                  result = generateResult(tmpPages, getWikipedia()
                                                        .getWikiConfiguration()
                                                        .getMaxArticleSize());
                }
              }
              try {
                api.updatePage(getWikipedia(), page, text,
                               "Dump analysis for error n°" +
                                   algorithm.getErrorNumberString() + " (" +
                                   nbPages + " pages)",
                               true, true, false);
              } catch (APIException e) {
                if (EnumQueryResult.CONTENT_TOO_BIG.equals(
                        e.getQueryResult())) {
                  for (int i = 0; i < 100; i++) {
                    if (!tmpPages.isEmpty()) {
                      finished = false;
                      tmpPages.remove(tmpPages.size() - 1);
                    }
                  }
                  result = generateResult(tmpPages, getWikipedia()
                                                        .getWikiConfiguration()
                                                        .getMaxArticleSize());
                } else {
                  throw e;
                }
              }
            }
          }
        } catch (APIException e) {
          // Nothing
        }
      }
    }
  }

  /**
   * Called on the event dispatching thread (not on the worker thread)
   * after the <code>construct</code> method has returned.
   *
   * @see org.wikipediacleaner.gui.swing.basic.BasicWorker#finished()
   */
  /**
   *
   * @see org.wikipediacleaner.gui.swing.basic.BasicWorker#finished()
   */
  @Override
  public void finished() {
    super.finished();
    if (getWindow() != null) {
      StringBuilder message = new StringBuilder();
      message.append(GT.__("{0} page has been analyzed",
                           "{0} pages have been analyzed", countAnalyzed,
                           Integer.toString(countAnalyzed)));
      for (AlgorithmInformation algorithmInfo : selectedAlgorithms) {
        CheckErrorAlgorithm algorithm = algorithmInfo.algorithm;
        Map<String, Detection> pages = algorithmInfo.getDetections();
        message.append("\n");
        message.append(GT.__(
            "{0} page has been detected for algorithm {1}",
            "{0} pages have been detected for algorithm {1}", pages.size(),
            new Object[] {pages.size(), algorithm.getErrorNumberString() +
                                            " - " +
                                            algorithm.getShortDescription()}));
      }
      Utilities.displayInformationMessage(getWindow().getParentComponent(),
                                          message.toString());
    }
  }

  /**
   * Controller for background tasks.
   */
  private class CWController extends MediaWikiController {

    /**
     * @param listener Listener to MediaWiki events.
     */
    public CWController(MediaWikiListener listener) { super(listener); }

    /**
     * @param task Task to be performed in background.
     * @see
     *     org.wikipediacleaner.api.MediaWikiController#addTask(java.util.concurrent.Callable)
     */
    @Override
    public void addTask(Callable<?> task) {
      // hasFinished(); // To clean up done tasks
      super.addTask(task);
      while (getFirstResultIfDone() != null) {
        // Do nothing, done result is simply removed from the list of tasks to
        // clean up done tasks
      }
    }

    /**
     * @return True if all tasks are completed.
     */
    public boolean hasFinished() {
      while (hasRemainingTask()) {
        Future<?> result = getFirstResultIfDone();
        if (result == null) {
          return false;
        }
      }
      return !hasRemainingTask();
    }
  }

  /**
   * Background task.
   */
  private class CWPageCallable extends MediaWikiCallable<Page> {

    /** Page to analyze */
    private final Page page;

    /**
     * @param wikipedia Wikipedia.
     * @param listener Listener of MediaWiki events.
     * @param api MediaWiki API.
     * @param page Page.
     * @param checkWiki True if last version should be checked on wiki.
     */
    public CWPageCallable(EnumWikipedia wiki, MediaWikiListener listener,
                          API api, Page page) {
      super(wiki, listener, api);
      this.page = page;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public Page call() throws APIException {
      EnumWikipedia wiki = getWikipedia();
      PageAnalysis analysis = page.getAnalysis(page.getContents(), false);
      analysis.performFullPageAnalysis(analysisTime);
      Page currentPage = null;
      PageAnalysis currentAnalysis = null;
      for (AlgorithmInformation algorithm : selectedAlgorithms) {
        List<CheckErrorResult> errors = new ArrayList<>();
        boolean detected = false;
        if (!algorithm.algorithm.isInWhiteList(page.getTitle())) {
          long beginTime = System.nanoTime();
          if (algorithm.algorithm.analyze(analysis, errors, false)) {
            detected = true;
          }
          long endTime = System.nanoTime();
          algorithm.addTimeSpent(endTime - beginTime);
        }
        if (detected) {
          boolean detectionConfirmed = false;

          // Confirm detection
          if (checkWiki) {
            try {
              if (currentPage == null) {
                currentPage = DataManager.getPage(wiki, page.getTitle(), null,
                                                  null, null);
              }
              if (currentAnalysis == null) {
                api.retrieveContents(wiki, Collections.singleton(currentPage),
                                     false, false);
                if (currentPage.getContents().equals(page.getContents())) {
                  currentAnalysis = analysis;
                } else {
                  currentAnalysis =
                      currentPage.getAnalysis(currentPage.getContents(), false);
                  currentAnalysis.performFullPageAnalysis(analysisTime);
                }
              }
              if (Boolean.FALSE.equals(currentPage.isExisting())) {
                detectionConfirmed = false;
              } else if (currentAnalysis == analysis) {
                detectionConfirmed = true;
              } else {
                errors.clear();
                long beginTime = System.nanoTime();
                if (algorithm.algorithm.analyze(currentAnalysis, errors,
                                                false)) {
                  detectionConfirmed = true;
                }
                long endTime = System.nanoTime();
                algorithm.addTimeSpent(endTime - beginTime);
              }
            } catch (APIException e) {
              // Nothing to do
            }
          } else {
            detectionConfirmed = true;
            currentPage = page;
          }

          // Memorize detection
          if (detectionConfirmed) {
            logCW.info("Detection confirmed for " + page.getTitle() + ": " +
                       algorithm.algorithm.getErrorNumberString() + " - " +
                       algorithm.algorithm.getShortDescription());
            algorithm.addDetection(currentPage, errors);
            countDetections++;
          }
        }
      }
      countAnalyzed++;
      if (countAnalyzed % 100000 == 0) {
        reportProgress();
      }
      if (countAnalyzed % 1000 == 0) {
        setText(GT._T("{0} pages processed", Integer.toString(countAnalyzed)));
      }
      return page;
    }
  }

  /**
   * Process pages in the dump.
   */
  private class CWPageProcessor implements PageProcessor {

    /** Wiki */
    private final EnumWikipedia wiki;

    /** Listener */
    private final MediaWikiListener listener;

    /** Controller for background tasks */
    private final CWController controller;

    /** API */
    private final API api;

    /** Restrict the processing to this list of pages */
    private List<String> pagesList;

    /**
     * @param wiki Wiki.
     * @param listener Listener.
     */
    public CWPageProcessor(EnumWikipedia wiki, MediaWikiListener listener) {
      this.wiki = wiki;
      this.listener = listener;
      this.controller = new CWController(null);
      this.api = APIFactory.getAPI();
    }

    /**
     * @return Wiki.
     * @see org.wikipediacleaner.api.dump.PageProcessor#getWiki()
     */
    @Override
    public EnumWikipedia getWiki() {
      return wiki;
    }

    /**
     * Add a page to the list of pages to check.
     *
     * @param page Page to be checked.
     */
    public void addPage(Page page) {
      if (page == null) {
        return;
      }
      String title = page.getTitle();
      if (pagesList == null) {
        pagesList = new ArrayList<>();
      }
      if (!pagesList.contains(title)) {
        pagesList.add(title);
      }
    }

    /**
     * @param page Page.
     * @see
     *     org.wikipediacleaner.api.dump.PageProcessor#processPage(org.wikipediacleaner.api.data.Page)
     */
    @Override
    public void processPage(Page page) {
      if ((page != null) && page.isInMainNamespace()) {
        if ((pagesList == null) || pagesList.contains(page.getTitle())) {
          controller.addTask(new CWPageCallable(wiki, listener, api, page));
        }
      }
    }

    /**
     * @return True if all tasks are completed.
     */
    public boolean hasFinished() { return controller.hasFinished(); }
  }

  /**
   * Bean for holding detection results.
   */
  static class Detection implements Comparable<Detection> {

    /** Page name */
    public final String pageName;

    /** List of notices */
    public final List<String> notices;

    /** Maximum level for the errors */
    public final ErrorLevel maxLevel;

    /**
     * @param page Page.
     * @param errors List of errors.
     */
    public Detection(Page page, List<CheckErrorResult> errors) {
      this.pageName = page.getTitle();
      this.notices = new ArrayList<>();
      ErrorLevel tmpLevel = ErrorLevel.CORRECT;
      if (errors != null) {
        for (CheckErrorResult error : errors) {
          String contents = page.getContents();
          if (contents != null) {
            notices.add(new String(contents.substring(error.getStartPosition(),
                                                      error.getEndPosition())));
          }
          ErrorLevel currentLevel = error.getErrorLevel();
          if (currentLevel.ordinal() < tmpLevel.ordinal()) {
            tmpLevel = currentLevel;
          }
        }
      }
      this.maxLevel = tmpLevel;
    }

    /**
     * @param o
     * @return
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Detection o) {
      if (o == null) {
        return -1;
      }

      // Compare error level
      if (!maxLevel.equals(o.maxLevel)) {
        if (maxLevel.ordinal() < o.maxLevel.ordinal()) {
          return -1;
        }
        return 1;
      }

      // Compare pages
      if (pageName == null) {
        if (o.pageName == null) {
          return 0;
        }
        return 1;
      }
      if (o.pageName == null) {
        return -1;
      }
      return pageName.compareTo(o.pageName);
    }
  }

  /**
   * Bean for holding information about processing for an algorithm.
   */
  static class AlgorithmInformation {

    /** Algorithm. */
    final CheckErrorAlgorithm algorithm;

    /** Errors found. */
    private final Map<String, Detection> detections;

    /** Time spent in analysis. */
    private long timeSpent;

    /**
     * @param algorithm Algorithm.
     */
    private AlgorithmInformation(CheckErrorAlgorithm algorithm) {
      this.algorithm = algorithm;
      this.detections = new HashMap<>();
      this.timeSpent = 0;
    }

    /**
     * Create a list for information about processing algorithms.
     *
     * @param algorithms List of algorithms.
     * @return List of information initialized.
     */
    public static List<AlgorithmInformation>
    createList(List<CheckErrorAlgorithm> algorithms) {
      List<AlgorithmInformation> list = new ArrayList<>(algorithms.size());
      for (CheckErrorAlgorithm algorithm : algorithms) {
        list.add(new AlgorithmInformation(algorithm));
      }
      return list;
    }

    /**
     * @return Errors found.
     */
    public Map<String, Detection> getDetections() { return detections; }

    /**
     * @param page Page.
     * @param errors List of errors.
     */
    public void addDetection(Page page, List<CheckErrorResult> errors) {
      detections.put(page.getTitle(), new Detection(page, errors));
    }

    /**
     * @param time Time spent.
     */
    public void addTimeSpent(long time) { timeSpent += time; }

    /**
     * @return Time spent.
     */
    public long getTimeSpent() { return timeSpent; }
  }
}
