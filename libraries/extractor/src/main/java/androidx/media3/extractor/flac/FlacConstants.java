/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.media3.extractor.flac;

import androidx.media3.common.util.UnstableApi;

/** Defines constants used by the FLAC extractor. */
@UnstableApi
public final class FlacConstants {

  /** Size of the FLAC stream marker in bytes. */
  public static final int STREAM_MARKER_SIZE = 4;

  /** Size of the header of a FLAC metadata block in bytes. */
  public static final int METADATA_BLOCK_HEADER_SIZE = 4;

  /** Size of the FLAC stream info block (header included) in bytes. */
  public static final int STREAM_INFO_BLOCK_SIZE = 38;

  /**
   * Minimum size of a FLAC frame header in bytes.
   *
   * <p>This is a header with:
   *
   * <ol>
   *   <li>The standard mandatory first 4 bytes
   *   <li>A single byte coded number
   *   <li>No uncommon block size or uncommon sample rate
   *   <li>A CRC-8 byte
   * </ol>
   */
  public static final int MIN_FRAME_HEADER_SIZE = 6;

  /**
   * Maximum size of a FLAC frame header in bytes.
   *
   * <p>This is a header with:
   *
   * <ol>
   *   <li>The standard mandatory first 4 bytes
   *   <li>A 7 byte coded number
   *   <li>A 2 byte uncommon block size
   *   <li>A 2 byte uncommon sample rate
   *   <li>A CRC-8 byte
   * </ol>
   */
  public static final int MAX_FRAME_HEADER_SIZE = 16;

  /** Stream info metadata block type. */
  public static final int METADATA_TYPE_STREAM_INFO = 0;

  /** Seek table metadata block type. */
  public static final int METADATA_TYPE_SEEK_TABLE = 3;

  /** Vorbis comment metadata block type. */
  public static final int METADATA_TYPE_VORBIS_COMMENT = 4;

  /** Picture metadata block type. */
  public static final int METADATA_TYPE_PICTURE = 6;

  private FlacConstants() {}
}
