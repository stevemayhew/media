/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.text.subrip;

import static androidx.annotation.VisibleForTesting.PRIVATE;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Format.CueReplacementBehavior;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableList;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A {@link SubtitleParser} for SubRip. */
@UnstableApi
public final class SubripParser implements SubtitleParser {

  /**
   * The {@link CueReplacementBehavior} for consecutive {@link CuesWithTiming} emitted by this
   * implementation.
   */
  public static final @CueReplacementBehavior int CUE_REPLACEMENT_BEHAVIOR =
      Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;

  // Fractional positions for use when alignment tags are present.
  private static final float START_FRACTION = 0.08f;
  private static final float END_FRACTION = 1 - START_FRACTION;
  private static final float MID_FRACTION = 0.5f;

  private static final String TAG = "SubripParser";

  // Some SRT files don't include hours or milliseconds in the timecode, so we use optional groups.
  private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+)(?:,(\\d{3}))?";
  private static final Pattern SUBRIP_TIMING_LINE =
      Pattern.compile("\\s*(" + SUBRIP_TIMECODE + ")\\s*-->\\s*(" + SUBRIP_TIMECODE + ")\\s*");

  // NOTE: Android Studio's suggestion to simplify '\\}' is incorrect [internal: b/144480183].
  private static final Pattern SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}");
  private static final String SUBRIP_ALIGNMENT_TAG = "\\{\\\\an[1-9]\\}";

  // Alignment tags for SSA V4+.
  private static final String ALIGN_BOTTOM_LEFT = "{\\an1}";
  private static final String ALIGN_BOTTOM_MID = "{\\an2}";
  private static final String ALIGN_BOTTOM_RIGHT = "{\\an3}";
  private static final String ALIGN_MID_LEFT = "{\\an4}";
  private static final String ALIGN_MID_MID = "{\\an5}";
  private static final String ALIGN_MID_RIGHT = "{\\an6}";
  private static final String ALIGN_TOP_LEFT = "{\\an7}";
  private static final String ALIGN_TOP_MID = "{\\an8}";
  private static final String ALIGN_TOP_RIGHT = "{\\an9}";

  private final StringBuilder textBuilder;
  private final ArrayList<String> tags;
  private final ParsableByteArray parsableByteArray;

  public SubripParser() {
    textBuilder = new StringBuilder();
    tags = new ArrayList<>();
    parsableByteArray = new ParsableByteArray();
  }

  @Override
  public @CueReplacementBehavior int getCueReplacementBehavior() {
    return CUE_REPLACEMENT_BEHAVIOR;
  }

  @Override
  public void parse(
      byte[] data,
      int offset,
      int length,
      OutputOptions outputOptions,
      Consumer<CuesWithTiming> output) {
    parsableByteArray.reset(data, /* limit= */ offset + length);
    parsableByteArray.setPosition(offset);
    Charset charset = detectUtfCharset(parsableByteArray);

    @Nullable
    List<CuesWithTiming> cuesWithTimingBeforeRequestedStartTimeUs =
        outputOptions.startTimeUs != C.TIME_UNSET && outputOptions.outputAllCues
            ? new ArrayList<>()
            : null;
    @Nullable String currentLine;
    while ((currentLine = parsableByteArray.readLine(charset)) != null) {
      if (currentLine.isEmpty()) {
        // Skip blank lines.
        continue;
      }

      // Parse and check the index line.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping invalid index: " + currentLine);
        continue;
      }

      // Read and parse the timing line.
      currentLine = parsableByteArray.readLine(charset);
      if (currentLine == null) {
        Log.w(TAG, "Unexpected end");
        break;
      }

      long startTimeUs;
      long endTimeUs;
      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.matches()) {
        startTimeUs = parseTimecode(matcher, /* groupOffset= */ 1);
        endTimeUs = parseTimecode(matcher, /* groupOffset= */ 6);
      } else {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      }

      // Read and parse the text and tags.
      textBuilder.setLength(0);
      tags.clear();
      currentLine = parsableByteArray.readLine(charset);
      while (!TextUtils.isEmpty(currentLine)) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(processLine(currentLine, tags));
        currentLine = parsableByteArray.readLine(charset);
      }

      Spanned text = Html.fromHtml(textBuilder.toString());

      @Nullable String alignmentTag = null;
      for (int i = 0; i < tags.size(); i++) {
        String tag = tags.get(i);
        if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {
          alignmentTag = tag;
          // Subsequent alignment tags should be ignored.
          break;
        }
      }
      if (outputOptions.startTimeUs == C.TIME_UNSET || endTimeUs >= outputOptions.startTimeUs) {
        output.accept(
            new CuesWithTiming(
                ImmutableList.of(buildCue(text, alignmentTag)),
                startTimeUs,
                /* durationUs= */ endTimeUs - startTimeUs));
      } else if (cuesWithTimingBeforeRequestedStartTimeUs != null) {
        cuesWithTimingBeforeRequestedStartTimeUs.add(
            new CuesWithTiming(
                ImmutableList.of(buildCue(text, alignmentTag)),
                startTimeUs,
                /* durationUs= */ endTimeUs - startTimeUs));
      }
    }
    if (cuesWithTimingBeforeRequestedStartTimeUs != null) {
      for (CuesWithTiming cuesWithTiming : cuesWithTimingBeforeRequestedStartTimeUs) {
        output.accept(cuesWithTiming);
      }
    }
  }

  /**
   * Determine UTF encoding of the byte array from a byte order mark (BOM), defaulting to UTF-8 if
   * no BOM is found.
   */
  private Charset detectUtfCharset(ParsableByteArray data) {
    @Nullable Charset charset = data.readUtfCharsetFromBom();
    return charset != null ? charset : StandardCharsets.UTF_8;
  }

  /**
   * Trims and removes tags from the given line. The removed tags are added to {@code tags}.
   *
   * @param line The line to process.
   * @param tags A list to which removed tags will be added.
   * @return The processed line.
   */
  private String processLine(String line, ArrayList<String> tags) {
    line = line.trim();

    int removedCharacterCount = 0;
    StringBuilder processedLine = new StringBuilder(line);
    Matcher matcher = SUBRIP_TAG_PATTERN.matcher(line);
    while (matcher.find()) {
      String tag = matcher.group();
      tags.add(tag);
      int start = matcher.start() - removedCharacterCount;
      int tagLength = tag.length();
      processedLine.replace(start, /* end= */ start + tagLength, /* str= */ "");
      removedCharacterCount += tagLength;
    }

    return processedLine.toString();
  }

  /**
   * Build a {@link Cue} based on the given text and alignment tag.
   *
   * @param text The text.
   * @param alignmentTag The alignment tag, or {@code null} if no alignment tag is available.
   * @return Built cue
   */
  private Cue buildCue(Spanned text, @Nullable String alignmentTag) {
    Cue.Builder cue = new Cue.Builder().setText(text);
    if (alignmentTag == null) {
      return cue.build();
    }

    // Horizontal alignment.
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_MID_LEFT:
      case ALIGN_TOP_LEFT:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_START);
        break;
      case ALIGN_BOTTOM_RIGHT:
      case ALIGN_MID_RIGHT:
      case ALIGN_TOP_RIGHT:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_END);
        break;
      case ALIGN_BOTTOM_MID:
      case ALIGN_MID_MID:
      case ALIGN_TOP_MID:
      default:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE);
        break;
    }

    // Vertical alignment.
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_BOTTOM_MID:
      case ALIGN_BOTTOM_RIGHT:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_END);
        break;
      case ALIGN_TOP_LEFT:
      case ALIGN_TOP_MID:
      case ALIGN_TOP_RIGHT:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_START);
        break;
      case ALIGN_MID_LEFT:
      case ALIGN_MID_MID:
      case ALIGN_MID_RIGHT:
      default:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE);
        break;
    }

    return cue.setPosition(getFractionalPositionForAnchorType(cue.getPositionAnchor()))
        .setLine(getFractionalPositionForAnchorType(cue.getLineAnchor()), Cue.LINE_TYPE_FRACTION)
        .build();
  }

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    @Nullable String hours = matcher.group(groupOffset + 1);
    long timestampMs = hours != null ? Long.parseLong(hours) * 60 * 60 * 1000 : 0;
    timestampMs +=
        Long.parseLong(Assertions.checkNotNull(matcher.group(groupOffset + 2))) * 60 * 1000;
    timestampMs += Long.parseLong(Assertions.checkNotNull(matcher.group(groupOffset + 3))) * 1000;
    @Nullable String millis = matcher.group(groupOffset + 4);
    if (millis != null) {
      timestampMs += Long.parseLong(millis);
    }
    return timestampMs * 1000;
  }

  // TODO(b/289983417): Make package-private again, once it is no longer needed in
  // DelegatingSubtitleDecoderWithSubripParserTest.java (i.e. legacy subtitle flow is removed)
  @VisibleForTesting(otherwise = PRIVATE)
  public static float getFractionalPositionForAnchorType(@Cue.AnchorType int anchorType) {
    switch (anchorType) {
      case Cue.ANCHOR_TYPE_START:
        return START_FRACTION;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return MID_FRACTION;
      case Cue.ANCHOR_TYPE_END:
        return END_FRACTION;
      case Cue.TYPE_UNSET:
      default:
        // Should never happen.
        throw new IllegalArgumentException();
    }
  }
}
