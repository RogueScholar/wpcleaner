/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a0xx.a01x.a015;

import java.util.Collections;
import java.util.List;

import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmUnclosedTags;
import org.wikipediacleaner.api.data.contents.tag.HtmlTagType;
import org.wikipediacleaner.api.data.contents.tag.TagType;


/**
 * Algorithm for analyzing error 15 of check wikipedia project.
 * Error 15: Code not correct end
 */
public class CheckErrorAlgorithm015 extends CheckErrorAlgorithmUnclosedTags {

  /** List of tags managed by this error. */
  private static final List<TagType> TAGS = Collections.singletonList(HtmlTagType.CODE);

  public CheckErrorAlgorithm015() {
    super("Code not correct end");
  }

  /**
   * @return List of tags managed by this error.
   */
  @Override
  protected List<TagType> getTags() {
    return TAGS;
  }

  /**
   * @return True if full tags should be reported.
   */
  @Override
  protected boolean reportFullTags() {
    return true;
  }
}
