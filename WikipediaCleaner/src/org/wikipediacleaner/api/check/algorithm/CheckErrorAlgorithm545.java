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
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.constants.WPCConfiguration;
import org.wikipediacleaner.api.data.DataManager;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.Page.RelatedPages;
import org.wikipediacleaner.api.data.PageAnalysis;
import org.wikipediacleaner.api.data.PageElementTemplate;
import org.wikipediacleaner.api.data.PageElementTemplate.Parameter;
import org.wikipediacleaner.i18n.GT;

/**
 * Algorithm for analyzing error 545 of check wikipedia project.
 * Error 545: Template with deprecated parameter.
 */
public class CheckErrorAlgorithm545 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm545() {
    super("Template with deprecated parameter");
  }

  /**
   * Analyze a page to check if errors are present.
   *
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param onlyAutomatic True if analysis could be restricted to errors
   *     automatically fixed.
   * @return Flag indicating if the error was found.
   */
  @Override
  public boolean analyze(PageAnalysis analysis,
                         Collection<CheckErrorResult> errors,
                         boolean onlyAutomatic) {
    if (analysis == null) {
      return false;
    }

    // Analyze each template
    boolean result = false;
    for (String[] deprecatedParameter : deprecatedParameters) {
      if ((deprecatedParameter != null) && (deprecatedParameter.length > 1)) {

        // Retrieve templates
        String templateName = deprecatedParameter[0];
        List<PageElementTemplate> templates =
            analysis.getTemplates(templateName);
        if ((templates != null) && !templates.isEmpty()) {
          String parameterName = deprecatedParameter[1];
          String explanation =
              (deprecatedParameter.length > 2) ? deprecatedParameter[2] : null;
          for (PageElementTemplate template : templates) {
            int paramIndex = template.getParameterIndex(parameterName);
            if (paramIndex >= 0) {
              result = true;
              if (errors == null) {
                return true;
              }
              Parameter param = template.getParameter(paramIndex);
              if (param != null) {
                CheckErrorResult errorResult = createCheckErrorResult(
                    analysis, param.getBeginIndex(), param.getEndIndex());
                if (!StringUtils.isEmpty(explanation)) {
                  errorResult.addText(explanation);
                }
                errorResult.addReplacement("");
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
   * @return True if the error has a special list of pages.
   */
  @Override
  public boolean hasSpecialList() {
    List<String> categories = getTrackingCategories();
    return ((categories != null) && (!categories.isEmpty()));
  }

  /**
   * @return Tracking categories.
   */
  private List<String> getTrackingCategories() {
    String tmp = getSpecificProperty("categories", true, true, false);
    if ((tmp == null) || tmp.isEmpty()) {
      return null;
    }
    return WPCConfiguration.convertPropertyToStringList(tmp, false);
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
    List<String> categoriesName = getTrackingCategories();
    if ((categoriesName == null) || (categoriesName.isEmpty())) {
      return null;
    }
    List<Page> result = new ArrayList<>();
    API api = APIFactory.getAPI();
    for (String categoryName : categoriesName) {
      String title = wiki.getWikiConfiguration().getPageTitle(
          Namespace.CATEGORY, categoryName);
      Page category = DataManager.getPage(wiki, title, null, null, null);
      try {
        api.retrieveCategoryMembers(wiki, category, 0, false, limit);
        List<Page> tmp =
            category.getRelatedPages(RelatedPages.CATEGORY_MEMBERS);
        if (tmp != null) {
          result.addAll(tmp);
        }
      } catch (APIException e) {
        //
      }
    }
    return result;
  }

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /** Deprecated parameters for templates */
  private static final String PARAMETER_TEMPLATES = "templates";

  /**
   * Initialize settings for the algorithm.
   *
   * @see
   *     org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    String tmp = getSpecificProperty(PARAMETER_TEMPLATES, true, true, false);
    deprecatedParameters.clear();
    if (tmp != null) {
      List<String[]> tmpList =
          WPCConfiguration.convertPropertyToStringArrayList(tmp);
      if (tmpList != null) {
        deprecatedParameters.addAll(tmpList);
      }
    }
  }

  /** Deprecated parameters for templates */
  private final List<String[]> deprecatedParameters = new ArrayList<>();

  /**
   * @return Map of parameters (key=name, value=description).
   * @see
   *     org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#getParameters()
   */
  @Override
  public Map<String, String> getParameters() {
    Map<String, String> parameters = super.getParameters();
    parameters.put(PARAMETER_TEMPLATES,
                   GT._T("Templates with deprecated parameters"));
    return parameters;
  }
}
