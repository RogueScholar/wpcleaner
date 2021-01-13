/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2017  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */


package org.wikipediacleaner.api.linter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.wikipediacleaner.api.configuration.WikiConfiguration;
import org.wikipediacleaner.api.data.contents.ContentsInterval;


/**
 * Bean representing a Linter error.
 */
public class LinterError extends ContentsInterval {

  /** Page title */
  private final String page;

  /** Error type */
  private final String type;

  /** Parameters */
  private final Map<String, String> parameters;

  private final boolean multiPartTemplateBlock;

  private final String templateName;

  /**
   * Constructor.
   * 
   * @param page Page title.
   * @param type Error type.
   * @param parameters Parameters.
   * @param beginIndex Begin index of the error.
   * @param endIndex End index of the error.
   * @param multiPartTemplateBlock True if the problem comes from multiple templates.
   * @param templateName Name of the template if the problem comes from a template.
   */
  public LinterError(
      String page,
      String type,
      Map<String, String> parameters,
      int beginIndex, int endIndex,
      boolean multiPartTemplateBlock,
      String templateName) {
    super(beginIndex, endIndex);
    this.page = page;
    this.type = type;
    this.parameters = (parameters != null) ? new HashMap<>(parameters) : null;
    this.multiPartTemplateBlock = multiPartTemplateBlock;
    this.templateName = templateName;
  }

  /**
   * @return Page title.
   */
  public String getPage() {
    return page;
  }

  /**
   * @return Error type.
   */
  public String getType() {
    return type;
  }

  /**
   * @param config Wiki configuration.
   * @return Error type.
   */
  public String getTypeName(WikiConfiguration config) {
    if (config != null) {
      String result = config.getMessageByName("linter-category-" + type);
      if (result != null) {
        return result;
      }
    }
    return type;
  }

  /**
   * @return Parameters.
   */
  public Map<String, String> getParameters() {
    return (parameters != null) ? Collections.unmodifiableMap(parameters) : null;
  }

  /**
   * @return True if the problem comes from multiple template.
   */
  public boolean isMutiPartTemplateBlock() {
    return multiPartTemplateBlock;
  }

  /**
   * @return Name of the template if the problem comes from a template.
   */
  public String getTemplateName() {
    return templateName;
  }
}
