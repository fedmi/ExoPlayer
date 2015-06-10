/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text.subrip;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple SubRip parser.
 * <p/>
 * @see <a href="https://en.wikipedia.org/wiki/SubRip">Wikipedia on SubRip</a>
 */
public final class SubripParser implements SubtitleParser {

  private static final Pattern SUBRIP_TIMING_LINE = Pattern.compile("(.*)\\s+-->\\s+(.*)");
  private static final Pattern SUBRIP_TIMESTAMP =
      Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+),(\\d+)");

  private final StringBuilder textBuilder;

  public SubripParser() {
    textBuilder = new StringBuilder();
  }

  @Override
  public SubripSubtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
          throws IOException {
    ArrayList<Cue> cues = new ArrayList<>();
    ArrayList<Long> cueTimesUs = new ArrayList<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, C.UTF8_NAME));
    String currentLine;

    while ((currentLine = reader.readLine()) != null) {
      // Parse the numeric counter as a sanity check.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        throw new ParserException("Expected numeric counter: " + currentLine);
      }

      // Read and parse the timing line.
      currentLine = reader.readLine();
      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.find()) {
        cueTimesUs.add(startTimeUs + parseTimestampUs(matcher.group(1)));
        cueTimesUs.add(startTimeUs + parseTimestampUs(matcher.group(2)));
      } else {
        throw new ParserException("Expected timing line: " + currentLine);
      }

      // Read and parse the text.
      textBuilder.setLength(0);
      while (!TextUtils.isEmpty(currentLine = reader.readLine())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(currentLine.trim());
      }

      Spanned text = Html.fromHtml(textBuilder.toString());
      cues.add(new Cue(text));
    }

    reader.close();
    inputStream.close();

    Cue[] cuesArray = new Cue[cues.size()];
    cues.toArray(cuesArray);
    long[] cueTimesUsArray = Util.toLongArray(cueTimesUs);
    return new SubripSubtitle(startTimeUs, cuesArray, cueTimesUsArray);
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_SUBRIP.equals(mimeType);
  }

  private static long parseTimestampUs(String s) throws NumberFormatException {
    Matcher matcher = SUBRIP_TIMESTAMP.matcher(s);
    if (!matcher.matches()) {
      throw new NumberFormatException("has invalid format");
    }
    long timestampMs = Long.parseLong(matcher.group(1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(4));
    return timestampMs * 1000;
  }

}
