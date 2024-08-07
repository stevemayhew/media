/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.decoder.iamf;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test IAMF native functions. */
@RunWith(AndroidJUnit4.class)
public final class IamfDecoderTest {

  @Before
  public void setUp() {
    assertThat(IamfLibrary.isAvailable()).isTrue();
  }

  @Test
  public void iamfLayoutBinauralChannelsCountTest() {
    IamfDecoder iamf = new IamfDecoder();
    assertThat(iamf.getBinauralLayoutChannelCount()).isEqualTo(2);
  }
}
