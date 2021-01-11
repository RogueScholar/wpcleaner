/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2020  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */


package org.wikipediacleaner.api.data.contents.tag;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Builder class.
 */
public class TagBuilder {

  /** Name of the tag */
  @Nonnull
  private final String name;

  /** Format of the tag */
  @Nonnull
  private final TagFormat format;

  /** List of attributes */
  @Nonnull
  private final List<ImmutablePair<String, String>> attributes;

  /**
   * Private constructor.
   * 
   * @param tagName Name of the tag.
   * @param format Format of the tag.
   */
  private TagBuilder(@Nonnull String name, @Nonnull TagFormat format) {
    this.name = name;
    this.format = format;
    this.attributes = new ArrayList<>();
  }

  /**
   * Initialize a builder with the name of the tag.
   * 
   * @param tagName Name of the tag.
   * @param format Format of the tag.
   * @return Builder initialized with the name and format of the tag.
   */
  public static @Nonnull TagBuilder from(@Nonnull String tagName, @Nonnull TagFormat format) {
    TagBuilder builder = new TagBuilder(tagName, format);
    return builder;
  }

  /**
   * Initialize a builder with the type of the tag.
   * 
   * @param tagType Type of the tag.
   * @param format Format of the tag.
   * @return Builder initialized with the name and format of the tag.
   */
  public static @Nonnull TagBuilder from(@Nonnull TagType tagType, @Nonnull TagFormat format) {
    TagBuilder builder = new TagBuilder(tagType.getNormalizedName(), format);
    return builder;
  }

  /**
   * Initialize a builder with the name of the tag.
   * 
   * @param tagName Name of the tag.
   * @param closing True if it's a closing tag.
   * @param full True if it's a full tag.
   * @return Builder initialized with the name and format of the tag.
   */
  public static @Nonnull TagBuilder from(@Nonnull String tagName, boolean closing, boolean full) {
    return from(
        tagName,
        full ? TagFormat.FULL : closing ? TagFormat.CLOSE : TagFormat.OPEN);
  }

  /**
   * Initialize a builder with the type of the tag.
   * 
   * @param tagType Type of the tag.
   * @param closing True if it's a closing tag.
   * @param full True if it's a full tag.
   * @return Builder initialized with the name and format of the tag.
   */
  public static @Nonnull TagBuilder from(@Nonnull TagType tagType, boolean closing, boolean full) {
    return from(
        tagType.getNormalizedName(),
        full ? TagFormat.FULL : closing ? TagFormat.CLOSE : TagFormat.OPEN);
  }

  /**
   * Add an attribute to the builder.
   * 
   * @param attributeName Name of the attribute.
   * @param attributeValue Value of the attribute.
   * @return Builder with the added attribute.
   */
  public @Nonnull TagBuilder addAttribute(@Nonnull String attributeName, @Nullable String attributeValue) {
    attributes.add(new ImmutablePair<>(attributeName, attributeValue));
    return this;
  }

  /**
   * @return Textual representation of the comment.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('<');
    if (TagFormat.CLOSE == format) {
      sb.append('/');
    }
    sb.append(name);
    if (TagFormat.CLOSE != format) {
      for (ImmutablePair<String, String> attribute : attributes) {
        sb.append(' ');
        sb.append(attribute.getLeft());
        if (attribute.getValue() != null) {
          sb.append("=\"");
          sb.append(attribute.getRight());
          sb.append("\"");
        }
      }
    }
    if (TagFormat.FULL == format) {
      sb.append(" /");
    }
    sb.append('>');
    return sb.toString();
  }
}
