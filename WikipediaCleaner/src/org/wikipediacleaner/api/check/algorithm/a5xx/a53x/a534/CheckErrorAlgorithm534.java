/*
 *  WPCleaner: A tool to help on Wikipedia maintenance tasks.
 *  Copyright (C) 2013  Nicolas Vervelle
 *
 *  See README.txt file for licensing information.
 */

package org.wikipediacleaner.api.check.algorithm.a5xx.a53x.a534;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.wikipediacleaner.api.API;
import org.wikipediacleaner.api.APIException;
import org.wikipediacleaner.api.APIFactory;
import org.wikipediacleaner.api.check.CheckErrorResult;
import org.wikipediacleaner.api.check.CheckErrorResult.ErrorLevel;
import org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase;
import org.wikipediacleaner.api.configuration.WikiConfiguration;
import org.wikipediacleaner.api.constants.EnumWikipedia;
import org.wikipediacleaner.api.data.LinterCategory;
import org.wikipediacleaner.api.data.MagicWord;
import org.wikipediacleaner.api.data.Namespace;
import org.wikipediacleaner.api.data.Page;
import org.wikipediacleaner.api.data.PageElementFunction;
import org.wikipediacleaner.api.data.PageElementImage;
import org.wikipediacleaner.api.data.PageElementImage.Parameter;
import org.wikipediacleaner.api.data.PageElementParameter;
import org.wikipediacleaner.api.data.PageElementTag;
import org.wikipediacleaner.api.data.analysis.PageAnalysis;
import org.wikipediacleaner.api.data.contents.comment.CommentBuilder;
import org.wikipediacleaner.api.data.contents.tag.WikiTagType;
import org.wikipediacleaner.api.data.PageElementTemplate;


/**
 * Algorithm for analyzing error 534 of check wikipedia project.
 * Error 534: Bogus image options (see [[Special:LintErrors/bogus-image-options]])
 */
public class CheckErrorAlgorithm534 extends CheckErrorAlgorithmBase {

  public CheckErrorAlgorithm534() {
    super("Bogus image options");
  }

  /** Bean for holding automatic replacements */
  public static class AutomaticReplacement {

    /** Initial text */
    public final String initialText;

    /** Target magic word */
    public final String targetMagicWord;

    /** Target text */
    public final String targetText;

    /** True if replacement can be done automatically */
    public final boolean automatic;

    /** List of magic words with potential options */
    private final static String[] mwOptions = {
      MagicWord.IMG_ALT,
      MagicWord.IMG_BASELINE,
      MagicWord.IMG_BORDER,
      MagicWord.IMG_BOTTOM,
      MagicWord.IMG_CENTER,
      MagicWord.IMG_CLASS,
      MagicWord.IMG_FRAMED,
      MagicWord.IMG_FRAMELESS,
      MagicWord.IMG_LANG,
      MagicWord.IMG_LEFT,
      MagicWord.IMG_LINK,
      MagicWord.IMG_LOSSY,
      MagicWord.IMG_MANUAL_THUMB,
      MagicWord.IMG_MIDDLE,
      MagicWord.IMG_NONE,
      MagicWord.IMG_PAGE,
      MagicWord.IMG_RIGHT,
      MagicWord.IMG_SUB,
      MagicWord.IMG_SUPER,
      MagicWord.IMG_TEXT_BOTTOM,
      MagicWord.IMG_TEXT_TOP,
      MagicWord.IMG_THUMBNAIL,
      MagicWord.IMG_TOP,
      MagicWord.IMG_UPRIGHT,
      MagicWord.IMG_WIDTH,
    };

    /**
     * Extra characters that can be added to the parameter
     */
    private static String EXTRA_CHARACTERS = "abcdefghijklmnopqrstuvwxyz=";

    /**
     * @param initialText Initial text.
     * @param targetMagicWord Target magic word.
     * @param targetText Target text.
     * @param automatic True if replacement can be done automatically.
     */
    public AutomaticReplacement(
        String initialText,
        String targetMagicWord, String targetText,
        boolean automatic) {
      this.initialText = initialText;
      this.targetMagicWord = targetMagicWord;
      this.targetText = targetText;
      this.automatic = automatic;
    }

    /**
     * @param replacements List of automatic replacements.
     * @param config Wiki configuration.
     * @param param Current parameter.
     * @param params All parameters.
     * @return Replacement for the initial text.
     */
    public static AutomaticReplacement suggestReplacement(
        AutomaticReplacement[] replacements,
        WikiConfiguration config,
        Parameter param, Collection<Parameter> params) {

      // Check parameters
      if ((replacements == null) ||
          (config == null) ||
          (param == null) ||
          (param.getContents() == null)) {
        return null;
      }
      String initialText = param.getContents().trim();

      // Find a suggestion
      AutomaticReplacement replacement = findSuggestion(
          replacements, config, initialText);

      // Handle specifically a second description
      if ((replacement == null) && (param.getMagicWord() == null)) {
        String secondDescription = null;
        boolean hasAltDescription = false;
        for (Parameter paramTmp : params) {
          if ((paramTmp != param) && (paramTmp.getMagicWord() == null)) {
            secondDescription = paramTmp.getContents();
          }
          if ((paramTmp.getMagicWord() != null) &&
              MagicWord.IMG_ALT.equals(paramTmp.getMagicWord().getName())) {
            hasAltDescription = true;
          }
        }
        if (secondDescription != null) {
          if (secondDescription.equals(initialText)) {
            return new AutomaticReplacement(initialText, null, null, true);
          }
          if (!hasAltDescription) {
            String targetText = "alt=" + initialText;
            MagicWord alt = config.getMagicWordByName(MagicWord.IMG_ALT);
            if ((alt != null) && (alt.isPossibleAlias(targetText))) {
              return new AutomaticReplacement(
                  initialText, MagicWord.IMG_ALT, targetText, false);
            }
          }
        }
      }
      if (replacement == null) {
        return null;
      }

      // Check that the suggestion can be applied
      if (replacement.targetMagicWord != null) {
        String mwName = replacement.targetMagicWord;
        boolean paramFound = false;
        for (Parameter paramTmp : params) {
          if (paramTmp == param) {
            paramFound = true;
          } else if (paramTmp.getMagicWord() != null) {
            // If option already exists, remove the faulty one
            String mwNameTmp = paramTmp.getMagicWord().getName();
            if (mwName.equals(mwNameTmp)) {
              return new AutomaticReplacement(initialText, null, null, true);
            }

            // Format option: one of border and/or frameless, frame, thumb (or thumbnail)
            if (MagicWord.IMG_BORDER.equals(mwName) ||
                MagicWord.IMG_FRAMELESS.equals(mwName) ||
                MagicWord.IMG_FRAMED.equals(mwName) ||
                MagicWord.IMG_THUMBNAIL.equals(mwName)) {
              if (MagicWord.IMG_BORDER.equals(mwNameTmp) ||
                  MagicWord.IMG_FRAMELESS.equals(mwNameTmp) ||
                  MagicWord.IMG_FRAMED.equals(mwNameTmp) ||
                  MagicWord.IMG_THUMBNAIL.equals(mwNameTmp)) {
                return new AutomaticReplacement(initialText, null, null, !paramFound);
              }
            } else
            // Horizontal alignment option: one of left, right, center, none
            if (MagicWord.IMG_CENTER.equals(mwName) ||
                MagicWord.IMG_LEFT.equals(mwName) ||
                MagicWord.IMG_NONE.equals(mwName) ||
                MagicWord.IMG_RIGHT.equals(mwName)) {
              if (MagicWord.IMG_CENTER.equals(mwNameTmp) ||
                  MagicWord.IMG_LEFT.equals(mwNameTmp) ||
                  MagicWord.IMG_NONE.equals(mwNameTmp) ||
                  MagicWord.IMG_RIGHT.equals(mwNameTmp)) {
                return new AutomaticReplacement(initialText, null, null, !paramFound);
              }
            } else
            // Vertical alignment option: one of baseline, sub, super, top, text-top, middle, bottom, text-bottom
            if (MagicWord.IMG_BASELINE.equals(mwName) ||
                MagicWord.IMG_BOTTOM.equals(mwName) ||
                MagicWord.IMG_MIDDLE.equals(mwName) ||
                MagicWord.IMG_SUB.equals(mwName) ||
                MagicWord.IMG_SUPER.equals(mwName) ||
                MagicWord.IMG_TEXT_BOTTOM.equals(mwName) ||
                MagicWord.IMG_TEXT_TOP.equals(mwName) ||
                MagicWord.IMG_TOP.equals(mwName)) {
              if (MagicWord.IMG_BASELINE.equals(mwName) ||
                  MagicWord.IMG_BOTTOM.equals(mwName) ||
                  MagicWord.IMG_MIDDLE.equals(mwName) ||
                  MagicWord.IMG_SUB.equals(mwName) ||
                  MagicWord.IMG_SUPER.equals(mwName) ||
                  MagicWord.IMG_TEXT_BOTTOM.equals(mwName) ||
                  MagicWord.IMG_TEXT_TOP.equals(mwName) ||
                  MagicWord.IMG_TOP.equals(mwName)) {
                return new AutomaticReplacement(initialText, null, null, !paramFound);
              }
            }
          }
        }
      }

      return replacement;
    }

    /**
     * Find a suggestion for replacement.
     * 
     * @param replacements List of automatic replacements.
     * @param config Wiki configuration.
     * @param initialText Initial text.
     * @return Replacement for the initial text.
     */
    private static AutomaticReplacement findSuggestion(
        AutomaticReplacement[] replacements,
        WikiConfiguration config,
        String initialText) {

      // Find a direct suggestion
      for (AutomaticReplacement replacement : replacements) {
        if (initialText.equals(replacement.initialText)) {
          if (replacement.targetMagicWord == null) {
            return replacement;
          }
          MagicWord magicWord = config.getMagicWordByName(replacement.targetMagicWord);
          if ((magicWord != null) && (magicWord.isPossibleAlias(replacement.targetText))) {
            return replacement;
          }
        }
      }

      // Find a suggestion ignoring case
      for (AutomaticReplacement replacement : replacements) {
        if (initialText.equalsIgnoreCase(replacement.initialText)) {
          if (replacement.targetMagicWord == null) {
            return replacement;
          }
          MagicWord magicWord = config.getMagicWordByName(replacement.targetMagicWord);
          if ((magicWord != null) && (magicWord.isPossibleAlias(replacement.targetText))) {
            return replacement;
          }
        }
      }

      // Special handling for all digits values
      boolean allNumeric = true;
      for (int pos = 0; (pos < initialText.length()) && allNumeric; pos++) {
        allNumeric &= Character.isDigit(initialText.charAt(pos));
      }
      if (allNumeric) {
        MagicWord magicWord = config.getMagicWordByName(MagicWord.IMG_WIDTH);
        if ((magicWord != null) && (magicWord.getAliases() != null)) {
          for (String alias : magicWord.getAliases()) {
            int variablePos = alias.indexOf("$1");
            if (variablePos >= 0) {
              String newText = alias.substring(0, variablePos) + initialText + alias.substring(variablePos + 2);
              return new AutomaticReplacement(
                  initialText, MagicWord.IMG_WIDTH, newText,
                  (initialText.length() > 2) && (initialText.length() < 4));
            }
          }
        }
      }

      // Find other suggestions
      AutomaticReplacement possible = findOtherSuggestion(config, initialText, true, true);
      if (possible != null) {
        return possible;
      }
      int variablePos = initialText.indexOf('=');
      if (variablePos < 0) {
        variablePos = initialText.length();
      }
      for (int letterIndex = 0; letterIndex < variablePos; letterIndex++) {
        StringBuilder modified = new StringBuilder(initialText.length() + 1);

        // Try adding a letter
        for (int letter = 0; letter < EXTRA_CHARACTERS.length(); letter++) {
          modified.setLength(0);
          if (letterIndex > 0) {
            modified.append(initialText.substring(0, letterIndex));
          }
          modified.append(EXTRA_CHARACTERS.charAt(letter));
          if (letterIndex < initialText.length()) {
            modified.append(initialText.substring(letterIndex));
          }
          possible = findOtherSuggestion(config, modified.toString(), false, false);
          if (possible != null) {
            return possible;
          }
        }

        // Try replacing a letter
        for (int letter = 0; letter < EXTRA_CHARACTERS.length(); letter++) {
          modified.setLength(0);
          if (letterIndex > 0) {
            modified.append(initialText.substring(0, letterIndex));
          }
          modified.append(EXTRA_CHARACTERS.charAt(letter));
          if (letterIndex < initialText.length()) {
            modified.append(initialText.substring(letterIndex + 1));
          }
          possible = findOtherSuggestion(config, modified.toString(), false, false);
          if (possible != null) {
            return possible;
          }
        }

        // Try removing a letter
        if (variablePos > 1) {
          modified.setLength(0);
          if (letterIndex > 0) {
            modified.append(initialText.substring(0, letterIndex));
          }
          if (letterIndex + 1 < initialText.length()) {
            modified.append(initialText.substring(letterIndex + 1));
          }
          possible = findOtherSuggestion(config, modified.toString(), false, false);
          if (possible != null) {
            return possible;
          }
        }
      }

      return null;
    }

    /**
     * Find an other suggestion for replacement.
     * 
     * @param config Wiki configuration.
     * @param initialText Initial text.
     * @param automatic True if replacement can be automatic.
     * @param complex True if complex replacements can be suggested.
     * @return Replacement for the initial text.
     */
    private static AutomaticReplacement findOtherSuggestion(
        WikiConfiguration config,
        String initialText,
        boolean automatic,
        boolean complex) {
      for (String mwName : mwOptions) {
        MagicWord mw = config.getMagicWordByName(mwName);
        if ((mw != null) && (mw.getAliases() != null)) {
          for (String alias : mw.getAliases()) {
            int variablePos = alias.indexOf("$1");
            if (variablePos < 0) {
              if (initialText.equalsIgnoreCase(alias)) {
                return new AutomaticReplacement(initialText, mwName, alias, automatic);
              }
            } else {

              // Check possibility to use the option
              boolean ok = true;
              final int prefixLength = variablePos;
              int newPrefixLength = prefixLength;
              final int suffixLength = alias.length() - 2 - variablePos;
              int newSuffixLength = suffixLength;
              if (initialText.length() < newPrefixLength + newSuffixLength) {
                ok = false;
              } else {
                int equalIndex = alias.indexOf('=');
                if (newPrefixLength > 0) {
                  // Check what is before the variable
                  if (equalIndex == variablePos - 1) {
                    if (!initialText.substring(0, equalIndex).equalsIgnoreCase(alias.substring(0, equalIndex))) {
                      ok = false;
                    } else {
                      int tmpIndex = equalIndex;
                      while ((tmpIndex < initialText.length()) &&
                             (initialText.charAt(tmpIndex) == ' ')) {
                        tmpIndex++;
                      }
                      if ((tmpIndex >= initialText.length()) ||
                          ("=:-)(".indexOf(initialText.charAt(tmpIndex)) < 0)) {
                        ok = false;
                      } else {
                        tmpIndex++;
                      }
                      while ((tmpIndex < initialText.length()) &&
                             (initialText.charAt(tmpIndex) == ' ')) {
                        tmpIndex++;
                      }
                      newPrefixLength = tmpIndex;
                    }
                  } else {
                    if (!initialText.substring(0, newPrefixLength).equalsIgnoreCase(alias.substring(0, newPrefixLength))) {
                      ok = false;
                    }
                  }
                }
                if (newSuffixLength > 0) {
                  // Check what is after the variable
                  String initialSuffix = initialText.substring(initialText.length() - newSuffixLength);
                  String aliasSuffix = alias.substring(alias.length() - newSuffixLength);
                  boolean suffixOk = false;
                  if (initialSuffix.equalsIgnoreCase(aliasSuffix)) {
                    suffixOk = true;
                  } else if (aliasSuffix.equals("px") && complex) {
                    int lastDigit = 0;
                    while (((lastDigit) < initialText.length()) &&
                           (Character.isDigit(initialText.charAt(lastDigit)))) {
                      lastDigit++;
                    }
                    if (lastDigit > 0) {
                      String currentSuffix = initialText.substring(lastDigit);
                      if (currentSuffix.equalsIgnoreCase("p") ||
                          currentSuffix.equalsIgnoreCase("x") ||
                          currentSuffix.equalsIgnoreCase("px") ||
                          currentSuffix.equalsIgnoreCase("xp")) {
                        suffixOk = true;
                        newSuffixLength = currentSuffix.length();
                      } else if ((currentSuffix.length() == 2) &&
                                 ((Character.toLowerCase(currentSuffix.charAt(0)) == 'p') ||
                                  (Character.toLowerCase(currentSuffix.charAt(1)) == 'x'))) {
                        suffixOk = true;
                        newSuffixLength = currentSuffix.length();
                      } else if ((currentSuffix.length() == 3) &&
                                 (Character.toLowerCase(currentSuffix.charAt(0)) == 'p') &&
                                 (Character.toLowerCase(currentSuffix.charAt(2)) == 'x')) {
                        suffixOk = true;
                        newSuffixLength = currentSuffix.length();
                      }
                    }
                  }
                  ok &= suffixOk;
                }
              }

              // Check special cases
              if (ok && MagicWord.IMG_WIDTH.equals(mw.getName())) {
                for (int index = newPrefixLength; (index < initialText.length() - newSuffixLength) && ok; index++) {
                  if (!Character.isDigit(initialText.charAt(index))) {
                    ok = false;
                  }
                }
              }

              // Use the option
              if (ok) {
                String newText =
                    alias.substring(0, prefixLength) +
                    initialText.substring(newPrefixLength, initialText.length() - newSuffixLength) +
                    alias.substring(alias.length() - suffixLength);
                if (mw.isPossibleAlias(newText)) {
                  return new AutomaticReplacement(initialText, mwName, newText, automatic);
                }
              }
            }
          }
        }
      }

      return null;
    }
  }

  /** List of automatic replacements */
  private static AutomaticReplacement[] automaticReplacements = {
    // Non existing options
    new AutomaticReplacement("align",      null, null, true),
    new AutomaticReplacement("alt",        null, null, true),
    new AutomaticReplacement("caption",    null, null, true),
    new AutomaticReplacement("default",    null, null, true),
    new AutomaticReplacement("file",       null, null, true),
    new AutomaticReplacement("horizontal", null, null, true),
    new AutomaticReplacement("landscape",  null, null, true),
    new AutomaticReplacement("links",      null, null, true),
    new AutomaticReplacement("logo",       null, null, true),
    new AutomaticReplacement("maxi",       null, null, true),
    new AutomaticReplacement("nothumb",    null, null, true),
    new AutomaticReplacement("panorama",   null, null, true),
    new AutomaticReplacement("portrait",   null, null, true),
    new AutomaticReplacement("small",      null, null, true),
    new AutomaticReplacement("text",       null, null, true),
    new AutomaticReplacement("title",      null, null, true),
    new AutomaticReplacement("vertical",   null, null, true),
    new AutomaticReplacement("view",       null, null, true),
    new AutomaticReplacement("wide",       null, null, true),

    // IMG_BORDER
    new AutomaticReplacement("rand", MagicWord.IMG_BORDER, "border", false), // de

    // IMG_CENTER
    new AutomaticReplacement("align=center", MagicWord.IMG_CENTER, "center", true),
    new AutomaticReplacement("align:center", MagicWord.IMG_CENTER, "center", true),
    new AutomaticReplacement("centro",       MagicWord.IMG_CENTER, "center", true),

    // IMG_FRAMELESS
    new AutomaticReplacement("rahmenlos", MagicWord.IMG_FRAMELESS, "frameless", true),
    new AutomaticReplacement("безрамки",  MagicWord.IMG_FRAMELESS, "frameless", true),

    // IMG_LEFT
    new AutomaticReplacement("align=left", MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("align:left", MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("esquerda",   MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("esquerra",   MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("gauche",     MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("izquierda",  MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("leftt",      MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("ліворуч",    MagicWord.IMG_LEFT, "left", true),
    new AutomaticReplacement("שמאל",       MagicWord.IMG_LEFT, "left", true),

    // IMG_RIGHT
    new AutomaticReplacement("align=right", MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("align:right", MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("derecha",     MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("desno",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("destra",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("direita",     MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("dreta",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("float right", MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("float=right", MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("float:right", MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("floatright",  MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("ight",        MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rechts",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("reght",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rght",        MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("ribght",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("richt",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righ",        MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righjt",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righr",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righte",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rightg",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rightl",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rightt",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rightx",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righty",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("right1",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("right2",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righy",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("righyt",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rigjt",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rignt",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rigt",        MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rigth",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rigtht",      MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rihgt",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("roght",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("rught",       MagicWord.IMG_RIGHT, "rigth", true),
    new AutomaticReplacement("праворуч",    MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("дясно",       MagicWord.IMG_RIGHT, "right", true),
    new AutomaticReplacement("справа",      MagicWord.IMG_RIGHT, "right", true),

    // IMG_THUMBNAIL
    new AutomaticReplacement("mini",              MagicWord.IMG_THUMBNAIL, "thumb", true), // de
    new AutomaticReplacement("miniatur",          MagicWord.IMG_THUMBNAIL, "thumb", true), // de
    new AutomaticReplacement("miniatura",         MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("miniaturadeimagen", MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("miniature",         MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("miniatyr",          MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("thum",              MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("thump",             MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("tuhmb",             MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("tumb",              MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("мини",              MagicWord.IMG_THUMBNAIL, "thumb", true),
    new AutomaticReplacement("ממוזער",              MagicWord.IMG_THUMBNAIL, "thumb", true),

    // IMG_UPRIGHT
    new AutomaticReplacement("align=upright", MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("hochkant",      MagicWord.IMG_UPRIGHT, "upright", true), // de
    new AutomaticReplacement("uoright",       MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("upleft",        MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("uprighht",      MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("uprigt",        MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("uprigth",       MagicWord.IMG_UPRIGHT, "upright", true),
    new AutomaticReplacement("uptight",       MagicWord.IMG_UPRIGHT, "upright", true),
  };

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

    // Analyze each image
    List<PageElementImage> images = analysis.getImages();
    String contents = analysis.getContents();
    boolean result = false;
    ArrayList<Parameter> params = new ArrayList<>();
    ArrayList<Parameter> paramsFormat = new ArrayList<>();
    ArrayList<Parameter> paramsHAlign = new ArrayList<>();
    ArrayList<Parameter> paramsVAlign = new ArrayList<>();
    HashMap<String, ArrayList<Parameter>> paramsOther = new HashMap<>();
    for (PageElementImage image : images) {

      // Analyze all parameters of the image
      params.clear();
      paramsFormat.clear();
      paramsHAlign.clear();
      paramsVAlign.clear();
      paramsOther.clear();
      Collection<Parameter> imageParameters = image.getParameters();
      if (imageParameters != null) {
        for (Parameter param : imageParameters) {
          if (param != null) {
            MagicWord mw = param.getMagicWord();
            if (mw == null) {
              // NO magic word: description or unknown
              params.add(param);
            } else {
              String mwName = mw.getName();
              // Format option: one of border and/or frameless, frame, thumb (or thumbnail)
              if (MagicWord.IMG_BORDER.equals(mwName)) {
                if (paramsFormat.isEmpty() ||
                    !MagicWord.IMG_FRAMELESS.equals(paramsFormat.get(0).getMagicWord().getName())) {
                  paramsFormat.add(param);
                }
              } else
              if (MagicWord.IMG_FRAMELESS.equals(mwName)) {
                if (paramsFormat.isEmpty() ||
                    !MagicWord.IMG_BORDER.equals(paramsFormat.get(0).getMagicWord().getName())) {
                  paramsFormat.add(param);
                }
              } else
              if (MagicWord.IMG_FRAMED.equals(mwName) ||
                  MagicWord.IMG_THUMBNAIL.equals(mwName)) {
                if (paramsFormat.isEmpty() ||
                    !MagicWord.IMG_BORDER.equals(paramsFormat.get(paramsFormat.size() - 1).getMagicWord().getName())) {
                  paramsFormat.add(param);
                } else {
                  paramsFormat.add(paramsFormat.size() - 1, param);
                }
              } else
              // Horizontal alignment option: one of left, right, center, none
              if (MagicWord.IMG_CENTER.equals(mwName) ||
                  MagicWord.IMG_LEFT.equals(mwName) ||
                  MagicWord.IMG_NONE.equals(mwName) ||
                  MagicWord.IMG_RIGHT.equals(mwName)) {
                paramsHAlign.add(param);
              } else
              // Vertical alignment option: one of baseline, sub, super, top, text-top, middle, bottom, text-bottom
              if (MagicWord.IMG_BASELINE.equals(mwName) ||
                  MagicWord.IMG_BOTTOM.equals(mwName) ||
                  MagicWord.IMG_MIDDLE.equals(mwName) ||
                  MagicWord.IMG_SUB.equals(mwName) ||
                  MagicWord.IMG_SUPER.equals(mwName) ||
                  MagicWord.IMG_TEXT_BOTTOM.equals(mwName) ||
                  MagicWord.IMG_TEXT_TOP.equals(mwName) ||
                  MagicWord.IMG_TOP.equals(mwName)) {
                paramsVAlign.add(param);
              } else {
                ArrayList<Parameter> tmpList = paramsOther.get(mwName);
                if (tmpList == null) {
                  tmpList = new ArrayList<>();
                  paramsOther.put(mwName, tmpList);
                }
                tmpList.add(param);
              }
            }
          }
        }
      }

      // Report images with several parameters that can't be related to a magic word
      if (params.size() > 1) {
        result = true;
        if (errors == null) {
          return result;
        }
        boolean reported = false;

        // Analyze if there's a risk of error
        boolean safe = true;
        boolean safeEmpty = true;
        String imageText = contents.substring(image.getBeginIndex(), image.getEndIndex());
        for (int index = 0; (index < imageText.length() - 1) && safeEmpty; index++) {
          char currentChar = imageText.charAt(index);
          if (currentChar == '{') {
            char nextChar = imageText.charAt(index + 1);
            if (nextChar == '|') {
              safe = false;
              safeEmpty = false;
            } else if (safe && (nextChar == '{')) {
              // TODO: analyze templates/functions/...
              PageElementTemplate template = analysis.isInTemplate(image.getBeginIndex() + index);
              PageElementFunction function = analysis.isInFunction(image.getBeginIndex() + index);
              PageElementParameter parameter = analysis.isInParameter(image.getBeginIndex() + index);
              if ((template != null) || (function != null) || (parameter != null)) {
                index++;
                safe = false;
              } else {
                safe = false;
              }
            }
          } else if (currentChar == '<') {
            PageElementTag tag = analysis.isInTag(image.getBeginIndex() + index);
            if (tag != null) {
              if (WikiTagType.CHEM.equals(tag.getType()) ||
                  WikiTagType.HIERO.equals(tag.getType()) ||
                  WikiTagType.MATH.equals(tag.getType()) ||
                  WikiTagType.MATH_CHEM.equals(tag.getType()) ||
                  WikiTagType.NOWIKI.equals(tag.getType()) ||
                  WikiTagType.REF.equals(tag.getType()) ||
                  WikiTagType.SCORE.equals(tag.getType()) ||
                  WikiTagType.SOURCE.equals(tag.getType()) ||
                  WikiTagType.SYNTAXHIGHLIGHT.equals(tag.getType())) {
                safe = false;
                safeEmpty = false;
              }
            }
          }
        }

        // Case when last parameter is empty
        if (!reported) {
          Parameter param = params.get(params.size() - 1);
          int beginIndex = image.getBeginIndex() + param.getBeginOffset();
          int endIndex = image.getBeginIndex() + param.getEndOffset();
          boolean hasContents = false;
          for (int index = beginIndex; (index < endIndex) && !hasContents; index++) {
            if (contents.charAt(index) != ' ') {
              hasContents = true;
            }
          }
          if (!hasContents) {
            CheckErrorResult errorResult = createCheckErrorResult(
                analysis, beginIndex - 1, endIndex,
                safe ? ErrorLevel.ERROR : ErrorLevel.WARNING);
            errorResult.addReplacement("", false);
            errors.add(errorResult);
            reported = true;
          }
        }

        // Check when last parameter is not empty
        if (!reported) {
          for (int numParam = 0; numParam < params.size(); numParam++) {
            Parameter param = params.get(numParam);
            int beginIndex = image.getBeginIndex() + param.getBeginOffset();
            int endIndex = image.getBeginIndex() + param.getEndOffset();
            if (numParam == params.size() - 1) {
              CheckErrorResult errorResult = createCheckErrorResult(
                  analysis, beginIndex, endIndex, ErrorLevel.CORRECT);
              errors.add(errorResult);
            } else {
              boolean hasContents = false;
              for (int index = beginIndex; (index < endIndex) && !hasContents; index++) {
                if (contents.charAt(index) != ' ') {
                  hasContents = true;
                }
              }
              CheckErrorResult errorResult = createCheckErrorResult(
                  analysis, beginIndex - 1, endIndex);
              if (!hasContents) {
                PageElementTemplate template = analysis.isInTemplate(beginIndex);
                if ((template != null) && (template.getBeginIndex() > image.getBeginIndex())) {
                  safeEmpty = false;
                }
                PageElementParameter parameter = analysis.isInParameter(beginIndex);
                if ((parameter != null) && (parameter.getBeginIndex() > image.getBeginIndex())) {
                  safeEmpty = false;
                }
                errorResult.addReplacement("", safeEmpty);
              } else {
                AutomaticReplacement replacement = AutomaticReplacement.suggestReplacement(
                    automaticReplacements, analysis.getWikiConfiguration(),
                    param, imageParameters);
                if (replacement != null) {
                  String text = replacement.targetText;
                  if ((text != null) && !text.isEmpty()) {
                    errorResult.addReplacement("|" + replacement.targetText, safe && replacement.automatic);
                  } else {
                    errorResult.addReplacement("", safe && replacement.automatic);
                  }
                }
              }
              errors.add(errorResult);
            }
          }
        }
      }

      // Report multiple options for several group of options
      result |= reportMultipleParameters(analysis, errors, image, paramsFormat, false);
      result |= reportMultipleParameters(analysis, errors, image, paramsHAlign, false);
      result |= reportMultipleParameters(analysis, errors, image, paramsVAlign, false);
      for (ArrayList<Parameter> paramOther : paramsOther.values()) {
        result |= reportMultipleParameters(analysis, errors, image, paramOther, params.isEmpty());
      }
    }

    return result;
  }

  /**
   * Report errors for multiple parameters of the same kind.
   * 
   * @param analysis Page analysis.
   * @param errors Errors found in the page.
   * @param image Image being analyzed.
   * @param params List of parameters of the same kind.
   * @return Flag indicating if the error was found.
   */
  private boolean reportMultipleParameters(
      PageAnalysis analysis, Collection<CheckErrorResult> errors,
      PageElementImage image, ArrayList<Parameter> params,
      boolean mayRemoveOne) {
    if ((params == null) || (params.size() < 2)) {
      return false;
    }
    if (mayRemoveOne && (params.size() < 3)) {
      return false;
    }
    if (errors == null) {
      return true;
    }
    boolean keepFirst = true;
    boolean useComments = false;
    Parameter paramKeep = params.get(0);
    if (paramKeep.getMagicWord() != null) {
      if (StringUtils.equals(MagicWord.IMG_ALT, paramKeep.getMagicWord().getName())) {
        keepFirst = false;
        useComments = true;
        paramKeep = params.get(params.size() - 1);
      } else if (StringUtils.equals(MagicWord.IMG_UPRIGHT, paramKeep.getMagicWord().getName())) {
        keepFirst = false; // Due to MW processing upright parameters in a different order: T216003
      } else if (StringUtils.equals(MagicWord.IMG_WIDTH, paramKeep.getMagicWord().getName())) {
        keepFirst = false; // Tests show that the last size is kept, not the first one
      }
    }
    int beginIndexKeep = image.getBeginIndex() + paramKeep.getBeginOffset();
    int endIndexKeep = image.getBeginIndex() + paramKeep.getEndOffset();
    for (int numParam = 1; numParam < params.size(); numParam++) {
      Parameter param = params.get(keepFirst ? numParam : numParam - 1);
      int beginIndex = image.getBeginIndex() + param.getBeginOffset();
      int endIndex = image.getBeginIndex() + param.getEndOffset();

      // Check if modifications can be automatic
      boolean automatic = true;
      if (analysis.isInFunction(beginIndex) != null) {
        automatic = false;
      }
      ErrorLevel errorLevel = ErrorLevel.ERROR;
      if (!paramKeep.getCorrect()) {
        if ((numParam == 1) && param.getCorrect()) {
          CheckErrorResult errorResult = createCheckErrorResult(
              analysis, beginIndexKeep - 1, endIndexKeep);
          errorResult.addReplacement("", automatic);
          errors.add(errorResult);
          errorLevel = ErrorLevel.WARNING;
        }
        if (param.getCorrect()) {
          automatic = false;
        }
      }
      if (automatic) {
        if ((analysis.isInParameter(beginIndex) != null) ||
            (analysis.isInFunction(beginIndex) != null)) {
          automatic = false;
        }
      }

      // Add error
      CheckErrorResult errorResult = createCheckErrorResult(
          analysis, beginIndex - 1, endIndex, errorLevel);
      errorResult.addReplacement("", automatic);
      errors.add(errorResult);

      // Handle comments
      if (useComments) {
        if (!param.getCorrect()) {
          useComments = false;
        }
        if (StringUtils.equals(param.getContents(), paramKeep.getContents())) {
          useComments = false;
        }
      }
      if (useComments) {
        errorResult = createCheckErrorResult(analysis, beginIndexKeep - 1, endIndexKeep);
        String contents = analysis.getContents();
        String replacement =
            contents.substring(beginIndexKeep - 1, endIndexKeep) +
            CommentBuilder.from(contents.substring(beginIndex, endIndex)).toString();
        errorResult.addReplacement(replacement, automatic);
        errors.add(errorResult);
      }
    }
    return true;
  }

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
   * @return True if the error has a special list of pages.
   */
  @Override
  public boolean hasSpecialList() {
    return (linterCategory != null);
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
    List<Page> result = null;
    if (linterCategory != null) {
      API api = APIFactory.getAPI();
      try {
        result = api.retrieveLinterCategory(
            wiki, linterCategory.getCategory(),
            Namespace.MAIN, false, true, limit);
      } catch (APIException e) {
        //
      }
    }
    return result;
  }

  /* ====================================================================== */
  /* PARAMETERS                                                             */
  /* ====================================================================== */

  /**
   * Initialize settings for the algorithm.
   * 
   * @see org.wikipediacleaner.api.check.algorithm.CheckErrorAlgorithmBase#initializeSettings()
   */
  @Override
  protected void initializeSettings() {
    List<LinterCategory> categories = getWikiConfiguration().getLinterCategories();
    if (categories != null) {
      for (LinterCategory category : categories) {
        if ("bogus-image-options".equals(category.getCategory())) {
          linterCategory = category;
        }
      }
    }
  }

  /** Linter category */
  private LinterCategory linterCategory = null;
}
