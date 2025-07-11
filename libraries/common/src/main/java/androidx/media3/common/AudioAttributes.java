/*
 * Copyright 2017 The Android Open Source Project
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
package androidx.media3.common;

import static android.os.Build.VERSION.SDK_INT;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Attributes for audio playback, which configure the underlying platform {@link
 * android.media.AudioTrack}.
 *
 * <p>To set the audio attributes, create an instance using the {@link Builder} and either pass it
 * to the player or send a message of type {@code Renderer#MSG_SET_AUDIO_ATTRIBUTES} to the audio
 * renderers.
 *
 * <p>This class is based on {@link android.media.AudioAttributes}, but can be used on all supported
 * API versions.
 */
public final class AudioAttributes {

  /** A direct wrapper around {@link android.media.AudioAttributes}. */
  public static final class AudioAttributesV21 {
    public final android.media.AudioAttributes audioAttributes;

    private AudioAttributesV21(AudioAttributes audioAttributes) {
      @SuppressLint("WrongConstant") // Setting C.AudioContentType and C.AudioUsage to platform API.
      android.media.AudioAttributes.Builder builder =
          new android.media.AudioAttributes.Builder()
              .setContentType(audioAttributes.contentType)
              .setFlags(audioAttributes.flags)
              .setUsage(audioAttributes.usage);
      if (SDK_INT >= 29) {
        Api29.setAllowedCapturePolicy(builder, audioAttributes.allowedCapturePolicy);
      }
      if (SDK_INT >= 32) {
        Api32.setSpatializationBehavior(builder, audioAttributes.spatializationBehavior);
        Api32.setIsContentSpatialized(builder, audioAttributes.isContentSpatialized);
      }
      this.audioAttributes = builder.build();
    }
  }

  /**
   * The default audio attributes, where the content type is {@link C#AUDIO_CONTENT_TYPE_UNKNOWN},
   * usage is {@link C#USAGE_MEDIA}, capture policy is {@link C#ALLOW_CAPTURE_BY_ALL} and no flags
   * are set.
   */
  public static final AudioAttributes DEFAULT = new Builder().build();

  /** Builder for {@link AudioAttributes}. */
  public static final class Builder {

    private @C.AudioContentType int contentType;
    private @C.AudioFlags int flags;
    private @C.AudioUsage int usage;
    private @C.AudioAllowedCapturePolicy int allowedCapturePolicy;
    private @C.SpatializationBehavior int spatializationBehavior;
    private boolean isContentSpatialized;

    /**
     * Creates a new builder for {@link AudioAttributes}.
     *
     * <p>By default the content type is {@link C#AUDIO_CONTENT_TYPE_UNKNOWN}, usage is {@link
     * C#USAGE_MEDIA}, capture policy is {@link C#ALLOW_CAPTURE_BY_ALL} and no flags are set.
     */
    public Builder() {
      contentType = C.AUDIO_CONTENT_TYPE_UNKNOWN;
      flags = 0;
      usage = C.USAGE_MEDIA;
      allowedCapturePolicy = C.ALLOW_CAPTURE_BY_ALL;
      spatializationBehavior = C.SPATIALIZATION_BEHAVIOR_AUTO;
      isContentSpatialized = false;
    }

    /** See {@link android.media.AudioAttributes.Builder#setContentType(int)} */
    @CanIgnoreReturnValue
    public Builder setContentType(@C.AudioContentType int contentType) {
      this.contentType = contentType;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setFlags(int)} */
    @CanIgnoreReturnValue
    public Builder setFlags(@C.AudioFlags int flags) {
      this.flags = flags;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setUsage(int)} */
    @CanIgnoreReturnValue
    public Builder setUsage(@C.AudioUsage int usage) {
      this.usage = usage;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setAllowedCapturePolicy(int)}. */
    @CanIgnoreReturnValue
    public Builder setAllowedCapturePolicy(@C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      this.allowedCapturePolicy = allowedCapturePolicy;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setSpatializationBehavior(int)}. */
    @CanIgnoreReturnValue
    public Builder setSpatializationBehavior(@C.SpatializationBehavior int spatializationBehavior) {
      this.spatializationBehavior = spatializationBehavior;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setIsContentSpatialized(boolean)}. */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setIsContentSpatialized(boolean isContentSpatialized) {
      this.isContentSpatialized = isContentSpatialized;
      return this;
    }

    /** Creates an {@link AudioAttributes} instance from this builder. */
    public AudioAttributes build() {
      return new AudioAttributes(
          contentType,
          flags,
          usage,
          allowedCapturePolicy,
          spatializationBehavior,
          isContentSpatialized);
    }
  }

  /** The {@link C.AudioContentType}. */
  public final @C.AudioContentType int contentType;

  /** The {@link C.AudioFlags}. */
  public final @C.AudioFlags int flags;

  /** The {@link C.AudioUsage}. */
  public final @C.AudioUsage int usage;

  /** The {@link C.AudioAllowedCapturePolicy}. */
  public final @C.AudioAllowedCapturePolicy int allowedCapturePolicy;

  /** The {@link C.SpatializationBehavior}. */
  public final @C.SpatializationBehavior int spatializationBehavior;

  /** Whether the content is spatialized. */
  @UnstableApi public final boolean isContentSpatialized;

  @Nullable private AudioAttributesV21 audioAttributesV21;

  private AudioAttributes(
      @C.AudioContentType int contentType,
      @C.AudioFlags int flags,
      @C.AudioUsage int usage,
      @C.AudioAllowedCapturePolicy int allowedCapturePolicy,
      @C.SpatializationBehavior int spatializationBehavior,
      boolean isContentSpatialized) {
    this.contentType = contentType;
    this.flags = flags;
    this.usage = usage;
    this.allowedCapturePolicy = allowedCapturePolicy;
    this.spatializationBehavior = spatializationBehavior;
    this.isContentSpatialized = isContentSpatialized;
  }

  /**
   * Returns a {@link AudioAttributesV21} from this instance.
   *
   * <p>Some fields are ignored if the corresponding {@link android.media.AudioAttributes.Builder}
   * setter is not available on the current API level.
   */
  public AudioAttributesV21 getAudioAttributesV21() {
    if (audioAttributesV21 == null) {
      audioAttributesV21 = new AudioAttributesV21(this);
    }
    return audioAttributesV21;
  }

  /** Returns the {@link C.StreamType} corresponding to these audio attributes. */
  @UnstableApi
  public @C.StreamType int getStreamType() {
    // Flags to stream type mapping
    if ((flags & C.FLAG_AUDIBILITY_ENFORCED) == C.FLAG_AUDIBILITY_ENFORCED) {
      return C.STREAM_TYPE_SYSTEM;
    }
    // Usage to stream type mapping
    switch (usage) {
      case C.USAGE_ASSISTANCE_SONIFICATION:
        return C.STREAM_TYPE_SYSTEM;
      case C.USAGE_VOICE_COMMUNICATION:
        return C.STREAM_TYPE_VOICE_CALL;
      case C.USAGE_VOICE_COMMUNICATION_SIGNALLING:
        return C.STREAM_TYPE_DTMF;
      case C.USAGE_ALARM:
        return C.STREAM_TYPE_ALARM;
      case C.USAGE_NOTIFICATION_RINGTONE:
        return C.STREAM_TYPE_RING;
      case C.USAGE_NOTIFICATION:
      case C.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
      case C.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
      case C.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
      case C.USAGE_NOTIFICATION_EVENT:
        return C.STREAM_TYPE_NOTIFICATION;
      case C.USAGE_ASSISTANCE_ACCESSIBILITY:
        return C.STREAM_TYPE_ACCESSIBILITY;
      case C.USAGE_MEDIA:
      case C.USAGE_GAME:
      case C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      case C.USAGE_ASSISTANT:
      case C.USAGE_UNKNOWN:
      default:
        return C.STREAM_TYPE_MUSIC;
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AudioAttributes other = (AudioAttributes) obj;
    return this.contentType == other.contentType
        && this.flags == other.flags
        && this.usage == other.usage
        && this.allowedCapturePolicy == other.allowedCapturePolicy
        && this.spatializationBehavior == other.spatializationBehavior
        && this.isContentSpatialized == other.isContentSpatialized;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + contentType;
    result = 31 * result + flags;
    result = 31 * result + usage;
    result = 31 * result + allowedCapturePolicy;
    result = 31 * result + spatializationBehavior;
    result = 31 * result + (isContentSpatialized ? 1 : 0);
    return result;
  }

  private static final String FIELD_CONTENT_TYPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_FLAGS = Util.intToStringMaxRadix(1);
  private static final String FIELD_USAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_ALLOWED_CAPTURE_POLICY = Util.intToStringMaxRadix(3);
  private static final String FIELD_SPATIALIZATION_BEHAVIOR = Util.intToStringMaxRadix(4);
  private static final String FIELD_IS_CONTENT_SPATIALIZED = Util.intToStringMaxRadix(5);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_CONTENT_TYPE, contentType);
    bundle.putInt(FIELD_FLAGS, flags);
    bundle.putInt(FIELD_USAGE, usage);
    bundle.putInt(FIELD_ALLOWED_CAPTURE_POLICY, allowedCapturePolicy);
    bundle.putInt(FIELD_SPATIALIZATION_BEHAVIOR, spatializationBehavior);
    bundle.putBoolean(FIELD_IS_CONTENT_SPATIALIZED, isContentSpatialized);
    return bundle;
  }

  /** Restores a {@code AudioAttributes} from a {@link Bundle}. */
  @UnstableApi
  public static AudioAttributes fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    if (bundle.containsKey(FIELD_CONTENT_TYPE)) {
      builder.setContentType(bundle.getInt(FIELD_CONTENT_TYPE));
    }
    if (bundle.containsKey(FIELD_FLAGS)) {
      builder.setFlags(bundle.getInt(FIELD_FLAGS));
    }
    if (bundle.containsKey(FIELD_USAGE)) {
      builder.setUsage(bundle.getInt(FIELD_USAGE));
    }
    if (bundle.containsKey(FIELD_ALLOWED_CAPTURE_POLICY)) {
      builder.setAllowedCapturePolicy(bundle.getInt(FIELD_ALLOWED_CAPTURE_POLICY));
    }
    if (bundle.containsKey(FIELD_SPATIALIZATION_BEHAVIOR)) {
      builder.setSpatializationBehavior(bundle.getInt(FIELD_SPATIALIZATION_BEHAVIOR));
    }
    if (bundle.containsKey(FIELD_IS_CONTENT_SPATIALIZED)) {
      builder.setIsContentSpatialized(bundle.getBoolean(FIELD_IS_CONTENT_SPATIALIZED));
    }
    return builder.build();
  }

  @RequiresApi(29)
  private static final class Api29 {
    @SuppressLint("WrongConstant") // Setting C.AudioAllowedCapturePolicy to platform API.
    public static void setAllowedCapturePolicy(
        android.media.AudioAttributes.Builder builder,
        @C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      builder.setAllowedCapturePolicy(allowedCapturePolicy);
    }
  }

  @RequiresApi(32)
  private static final class Api32 {
    @SuppressLint("WrongConstant") // Setting C.SpatializationBehavior to platform API.
    public static void setSpatializationBehavior(
        android.media.AudioAttributes.Builder builder,
        @C.SpatializationBehavior int spatializationBehavior) {
      builder.setSpatializationBehavior(spatializationBehavior);
    }

    public static void setIsContentSpatialized(
        android.media.AudioAttributes.Builder builder, boolean isContentSpatialized) {
      builder.setIsContentSpatialized(isContentSpatialized);
    }
  }
}
