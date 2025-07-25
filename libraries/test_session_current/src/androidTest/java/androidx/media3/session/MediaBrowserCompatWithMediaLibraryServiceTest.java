/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media.utils.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MediaLibraryService.MediaLibrarySession.LIBRARY_ERROR_REPLICATION_MODE_NONE;
import static androidx.media3.session.MediaLibraryService.MediaLibrarySession.LIBRARY_ERROR_REPLICATION_MODE_NON_FATAL;
import static androidx.media3.session.MockMediaLibraryService.CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT;
import static androidx.media3.session.MockMediaLibraryService.createNotifyChildrenChangedBundle;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT;
import static androidx.media3.session.legacy.MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ALBUM_TITLE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ARTIST;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ARTWORK_URI;
import static androidx.media3.test.session.common.CommonConstants.METADATA_EXTRA_KEY;
import static androidx.media3.test.session.common.CommonConstants.METADATA_EXTRA_VALUE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_MEDIA_URI;
import static androidx.media3.test.session.common.CommonConstants.METADATA_TITLE;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.MediaBrowserConstants.CHILDREN_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.CONNECTION_HINTS_KEY_LIBRARY_ERROR_REPLICATION_MODE;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_VALUE_PARTIAL_PROGRESS;
import static androidx.media3.test.session.common.MediaBrowserConstants.GET_CHILDREN_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_BROWSABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM_WITH_BROWSE_ACTIONS;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM_WITH_METADATA;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_PLAYABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_ALLOW_FIRST_ON_GET_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR_DEPRECATED;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_NO_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_SKIP_LIMIT_REACHED_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_KEY;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_VALUE;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_2;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.CustomActionCallback;
import android.support.v4.media.MediaBrowserCompat.ItemCallback;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserCompat.SearchCallback;
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import androidx.media3.test.session.common.MediaBrowserConstants;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.truth.os.BundleSubject;
import androidx.test.filters.LargeTest;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowserCompat} with {@link MediaLibraryService}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
@SuppressWarnings("deprecation") // Tests behavior of deprecated MediaBrowserCompat
public class MediaBrowserCompatWithMediaLibraryServiceTest
    extends MediaBrowserCompatWithMediaSessionServiceTest {

  @Override
  ComponentName getServiceComponent() {
    return MOCK_MEDIA3_LIBRARY_SERVICE;
  }

  @Test
  public void getRoot() throws Exception {
    // The MockMediaLibraryService gives MediaBrowserConstants.ROOT_ID as root ID, and
    // MediaBrowserConstants.ROOT_EXTRAS as extras.
    connectAndWait(/* rootHints= */ Bundle.EMPTY);

    assertThat(browserCompat.getRoot()).isEqualTo(ROOT_ID);
    assertThat(
            browserCompat
                .getExtras()
                .getInt(ROOT_EXTRAS_KEY, /* defaultValue= */ ROOT_EXTRAS_VALUE + 1))
        .isEqualTo(ROOT_EXTRAS_VALUE);
    ArrayList<Bundle> mediaItemCommandButtons =
        browserCompat
            .getExtras()
            .getParcelableArrayList(
                androidx.media3.session.legacy.MediaConstants
                    .BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST);
    assertThat(mediaItemCommandButtons).hasSize(2);
    assertThat(
            mediaItemCommandButtons
                .get(0)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID))
        .isEqualTo(MediaBrowserConstants.COMMAND_PLAYLIST_ADD);
    assertThat(
            mediaItemCommandButtons
                .get(0)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL))
        .isEqualTo("Add to playlist");
    assertThat(
            mediaItemCommandButtons
                .get(0)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI))
        .isEqualTo("content://playlist_add");
    assertThat(
            mediaItemCommandButtons
                .get(0)
                .getBundle(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS)
                .getString("key-1"))
        .isEqualTo("playlist_add");
    assertThat(
            mediaItemCommandButtons
                .get(1)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID))
        .isEqualTo(MediaBrowserConstants.COMMAND_RADIO);
    assertThat(
            mediaItemCommandButtons
                .get(1)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL))
        .isEqualTo("Radio station");
    assertThat(
            mediaItemCommandButtons
                .get(1)
                .getString(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI))
        .isEqualTo("content://radio");
    assertThat(
            mediaItemCommandButtons
                .get(1)
                .getBundle(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS)
                .getString("key-1"))
        .isEqualTo("radio");

    // Note: Cannot use equals() here because browser compat's extra contains server version,
    // extra binder, and extra messenger.
    assertThat(TestUtils.contains(browserCompat.getExtras(), ROOT_EXTRAS)).isTrue();
  }

  @Test
  public void getItem_browsable() throws Exception {
    String mediaId = MEDIA_ID_GET_BROWSABLE_ITEM;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    assertThat(itemRef.get().isBrowsable()).isTrue();
    assertThat(itemRef.get().getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_playable() throws Exception {
    String mediaId = MEDIA_ID_GET_PLAYABLE_ITEM;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    assertThat(itemRef.get().isPlayable()).isTrue();
    assertThat(itemRef.get().getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_playableWithBrowseActions_browseActionCorrectlyConverted() throws Exception {
    String mediaId = MEDIA_ID_GET_ITEM_WITH_BROWSE_ACTIONS;
    Bundle rootHints = new Bundle();
    rootHints.putInt(BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT, 10);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(
            itemRef
                .get()
                .getDescription()
                .getExtras()
                .getStringArrayList(DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST))
        .containsExactly(
            MediaBrowserConstants.COMMAND_PLAYLIST_ADD, MediaBrowserConstants.COMMAND_RADIO)
        .inOrder();
  }

  @Test
  public void getItem_maxCommandsForMediaItemSetToBelowMaxAvailableCommands_maxCommandsHonoured()
      throws Exception {
    String mediaId = MEDIA_ID_GET_ITEM_WITH_BROWSE_ACTIONS;
    Bundle rootHints = new Bundle();
    rootHints.putInt(BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT, 1);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(
            itemRef
                .get()
                .getDescription()
                .getExtras()
                .getStringArrayList(DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST))
        .containsExactly(MediaBrowserConstants.COMMAND_PLAYLIST_ADD);
  }

  @Test
  public void getItem_metadata() throws Exception {
    String mediaId = MEDIA_ID_GET_ITEM_WITH_METADATA;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    MediaDescriptionCompat description = itemRef.get().getDescription();
    assertThat(TextUtils.equals(description.getTitle(), METADATA_TITLE)).isTrue();
    assertThat(TextUtils.equals(description.getSubtitle(), METADATA_ARTIST)).isTrue();
    assertThat(TextUtils.equals(description.getDescription(), METADATA_ALBUM_TITLE)).isTrue();
    assertThat(description.getIconUri()).isEqualTo(METADATA_ARTWORK_URI);
    assertThat(description.getMediaUri()).isEqualTo(METADATA_MEDIA_URI);
    BundleSubject.assertThat(description.getExtras())
        .string(METADATA_EXTRA_KEY)
        .isEqualTo(METADATA_EXTRA_VALUE);
    assertThat(description.getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_nullResult() throws Exception {
    String mediaId = "random_media_id";
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean onItemLoadedCalled = new AtomicBoolean();
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    AtomicBoolean onErrorCalled = new AtomicBoolean();

    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            onItemLoadedCalled.set(true);
            itemRef.set(item);
            latch.countDown();
          }

          @Override
          public void onError(String itemId) {
            onErrorCalled.set(true);
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(onItemLoadedCalled.get()).isTrue();
    assertThat(itemRef.get()).isNull();
    assertThat(onErrorCalled.get()).isFalse();
  }

  @Test
  public void getItem_commandGetItemNotAvailable_reportsNull() throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);
    List<MediaItem> capturedMediaItems = new ArrayList<>();

    browserCompat.getItem(
        MEDIA_ID_GET_BROWSABLE_ITEM,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            capturedMediaItems.add(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(capturedMediaItems).containsExactly((Object) null);
  }

  @Test
  public void getChildren() throws Exception {
    String testParentId = PARENT_ID;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> parentIdRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> childrenRef = new AtomicReference<>();
    AtomicBoolean onChildrenLoadedWithBundleCalled = new AtomicBoolean();

    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            parentIdRef.set(parentId);
            childrenRef.set(children);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle option) {
            onChildrenLoadedWithBundleCalled.set(true);
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRef.get()).isEqualTo(testParentId);
    List<MediaItem> children = childrenRef.get();
    assertThat(children).hasSize(GET_CHILDREN_RESULT.size());
    // Compare the given results with originals.
    for (int i = 0; i < children.size(); i++) {
      MediaItem mediaItem = children.get(i);
      assertThat(mediaItem.getMediaId()).isEqualTo(GET_CHILDREN_RESULT.get(i));
      assertThat(
              mediaItem
                  .getDescription()
                  .getExtras()
                  .getInt(
                      EXTRAS_KEY_COMPLETION_STATUS,
                      /* defaultValue= */ EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED + 1))
          .isEqualTo(EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
      assertThat(mediaItem.getDescription().getIconBitmap()).isNotNull();
      assertThat(onChildrenLoadedWithBundleCalled.get()).isFalse();
      assertThat(
              mediaItem
                  .getDescription()
                  .getExtras()
                  .getStringArrayList(DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST))
          .containsExactly(
              MediaBrowserConstants.COMMAND_PLAYLIST_ADD, MediaBrowserConstants.COMMAND_RADIO)
          .inOrder();
    }
  }

  @Test
  public void getChildren_withLongList() throws Exception {
    String testParentId = PARENT_ID_LONG_LIST;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> parentIdRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> childrenRef = new AtomicReference<>();
    AtomicBoolean onChildrenLoadedWithBundleCalled = new AtomicBoolean();

    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            parentIdRef.set(parentId);
            childrenRef.set(children);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle option) {
            onChildrenLoadedWithBundleCalled.set(true);
          }
        });

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRef.get()).isEqualTo(testParentId);
    List<MediaItem> children = childrenRef.get();
    assertThat(children).isNotNull();
    assertThat(children.size()).isLessThan(LONG_LIST_COUNT);
    // Compare the given results with originals.
    for (int i = 0; i < children.size(); i++) {
      assertThat(children.get(i).getMediaId()).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
    assertThat(onChildrenLoadedWithBundleCalled.get()).isFalse();
  }

  @Test
  public void getChildren_withPagination() throws Exception {
    String testParentId = PARENT_ID;
    int page = 4;
    int pageSize = 10;
    Bundle extras = new Bundle();
    extras.putString(testParentId, testParentId);
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    Bundle option = new Bundle();
    option.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    option.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    AtomicReference<String> parentIdRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> childrenRef = new AtomicReference<>();
    AtomicBoolean onChildrenLoadedWithoutBundleCalled = new AtomicBoolean();

    browserCompat.subscribe(
        testParentId,
        option,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
            parentIdRef.set(parentId);
            childrenRef.set(children);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            onChildrenLoadedWithoutBundleCalled.set(true);
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRef.get()).isEqualTo(testParentId);
    assertThat(option.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(page);
    assertThat(option.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(pageSize);
    List<MediaItem> children = childrenRef.get();
    assertThat(children).isNotNull();
    int fromIndex = page * pageSize;
    int toIndex = Math.min((page + 1) * pageSize, CHILDREN_COUNT);
    // Compare the given results with originals.
    for (int originalIndex = fromIndex; originalIndex < toIndex; originalIndex++) {
      int relativeIndex = originalIndex - fromIndex;
      assertThat(children.get(relativeIndex).getMediaId())
          .isEqualTo(GET_CHILDREN_RESULT.get(originalIndex));
      assertThat(children.get(relativeIndex).getDescription().getIconBitmap()).isNotNull();
    }
    assertThat(onChildrenLoadedWithoutBundleCalled.get()).isFalse();
  }

  @Test
  public void
      getChildren_errorResultWithDefaultErrorReplication_legacyPlaybackStateWithNonFatalError()
          throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    subscribeAndAssertServiceCallbackErrorWithAuthErrorReplicated(
        PARENT_ID_AUTH_EXPIRED_ERROR, /* assertFatalError= */ false);
  }

  @Test
  public void
      getChildren_deprecatedErrorResultWithDefaultErrorReplication_legacyPlaybackStateWithNonFatalError()
          throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    // Tests the deprecated approach where apps were expected to pass the error extras back as the
    // extras of the LibraryParams of the LibraryResult because the SessionError type didn't then
    // exist as part of the LibraryResult.
    subscribeAndAssertServiceCallbackErrorWithAuthErrorReplicated(
        PARENT_ID_AUTH_EXPIRED_ERROR_DEPRECATED, /* assertFatalError= */ false);
  }

  @Test
  public void
      getChildren_errorResultWithNonFatalErrorReplication_legacyPlaybackStateWithNonFatalError()
          throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        CONNECTION_HINTS_KEY_LIBRARY_ERROR_REPLICATION_MODE,
        LIBRARY_ERROR_REPLICATION_MODE_NON_FATAL);
    connectForServiceStartWithConnectionHints(connectionHints);
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    // Tests the deprecated approach where apps were expected to pass the error extras back as the
    // extras of the LibraryParams of the LibraryResult because the SessionError type didn't then
    // exist as part of the LibraryResult.
    subscribeAndAssertServiceCallbackErrorWithAuthErrorReplicated(
        PARENT_ID_AUTH_EXPIRED_ERROR, /* assertFatalError= */ false);
  }

  private void subscribeAndAssertServiceCallbackErrorWithAuthErrorReplicated(
      String authExpiredParentId, boolean assertFatalError) throws Exception {
    CountDownLatch errorLatch = new CountDownLatch(1);
    AtomicReference<String> parentIdRefOnError = new AtomicReference<>();

    browserCompat.subscribe(
        authExpiredParentId,
        new SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            parentIdRefOnError.set(parentId);
            errorLatch.countDown();
          }
        });

    assertThat(errorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRefOnError.get()).isEqualTo(authExpiredParentId);
    assertThat(firstPlaybackStateCompatReported.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    if (assertFatalError) {
      assertThat(Iterables.getLast(reportedPlaybackStatesCompat).getState())
          .isEqualTo(PlaybackStateCompat.STATE_ERROR);
    } else {
      assertThat(Iterables.getLast(reportedPlaybackStatesCompat).getState())
          .isNotEqualTo(PlaybackStateCompat.STATE_ERROR);
    }
    assertThat(
            Iterables.getLast(reportedPlaybackStatesCompat)
                .getExtras()
                .getString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT))
        .isEqualTo(PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL);

    CountDownLatch successLatch = new CountDownLatch(1);
    AtomicReference<String> parentIdRefOnChildrenLoaded = new AtomicReference<>();

    browserCompat.subscribe(
        PARENT_ID,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            parentIdRefOnChildrenLoaded.set(parentId);
            successLatch.countDown();
          }
        });

    assertThat(successLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRefOnChildrenLoaded.get()).isEqualTo(PARENT_ID);
    // Any successful calls remove the error state,
    assertThat(Iterables.getLast(reportedPlaybackStatesCompat).getState())
        .isNotEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(
            Iterables.getLast(reportedPlaybackStatesCompat)
                .getExtras()
                .getString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT))
        .isNull();
  }

  @Test
  public void getChildren_errorResultWithErrorReplicationDisabled_errorNotReplicated()
      throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        CONNECTION_HINTS_KEY_LIBRARY_ERROR_REPLICATION_MODE, LIBRARY_ERROR_REPLICATION_MODE_NONE);
    connectForServiceStartWithConnectionHints(connectionHints);
    connectAndWait(/* rootHints= */ Bundle.EMPTY);

    subscribeAndAssertServiceCallbackErrorWithoutErrorReplication(PARENT_ID_AUTH_EXPIRED_ERROR);
  }

  @Test
  public void getChildren_skipLimitReachedErrorResultDefaultErrorCodeSet_errorNotReplicated()
      throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);

    subscribeAndAssertServiceCallbackErrorWithoutErrorReplication(
        PARENT_ID_SKIP_LIMIT_REACHED_ERROR);
  }

  private void subscribeAndAssertServiceCallbackErrorWithoutErrorReplication(String parentId)
      throws InterruptedException {
    CountDownLatch errorLatch = new CountDownLatch(1);
    AtomicReference<String> parentIdRefOnError = new AtomicReference<>();
    browserCompat.subscribe(
        parentId,
        new SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            parentIdRefOnError.set(parentId);
            errorLatch.countDown();
          }
        });

    assertThat(errorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRefOnError.get()).isEqualTo(parentId);
    assertThat(reportedPlaybackStatesCompat).isEmpty();
    assertThat(controllerCompat.getPlaybackState().getErrorCode()).isEqualTo(0);
    assertThat(controllerCompat.getPlaybackState().getErrorMessage()).isNull();

    // Trigger a playback state update to assert we never get a playback state with error reported.
    controllerCompat.getTransportControls().play();

    assertThat(firstPlaybackStateCompatReported.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(reportedPlaybackStatesCompat).isNotEmpty();
    assertThat(controllerCompat.getPlaybackState().getErrorCode()).isEqualTo(0);
    assertThat(controllerCompat.getPlaybackState().getErrorMessage()).isNull();
  }

  @Test
  public void getChildren_emptyResult() throws Exception {
    String testParentId = PARENT_ID_NO_CHILDREN;
    connectAndWait(/* rootHints= */ null);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<List<MediaItem>> childrenRef = new AtomicReference<>();

    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            childrenRef.set(children);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<MediaItem> children = childrenRef.get();
    assertThat(children).isNotNull();
    assertThat(children).isEmpty();
  }

  @Test
  public void getChildren_errorLibraryResult() throws Exception {
    String testParentId = PARENT_ID_ERROR;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> parentIdRef = new AtomicReference<>();

    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            parentIdRef.set(parentId);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRef.get()).isEqualTo(testParentId);
  }

  @Test
  public void subscribe_browserNotifyChildrenChanged_callsOnChildrenLoadedTwice() throws Exception {
    String testParentId = SUBSCRIBE_PARENT_ID_2;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(2);
    List<String> parentIds = new ArrayList<>();
    List<List<MediaItem>> childrenList = new ArrayList<>();
    Bundle requestNotifyChildrenWithDelayBundle =
        createNotifyChildrenChangedBundle(
            testParentId, /* itemCount= */ 12, /* delayMs= */ 100L, /* broadcast= */ false);
    requestNotifyChildrenWithDelayBundle.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, 12);

    browserCompat.subscribe(
        testParentId,
        requestNotifyChildrenWithDelayBundle,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
            parentIds.add(parentId);
            childrenList.add(children);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(testParentId, testParentId);
    assertThat(childrenList).hasSize(2);
    assertThat(childrenList.get(0)).hasSize(12);
    assertThat(childrenList.get(1)).hasSize(12);
  }

  @Test
  public void subscribe_onChildrenChangedWithMaxValue_convertedToOnError() throws Exception {
    String testParentId = PARENT_ID_ALLOW_FIRST_ON_GET_CHILDREN;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(2);
    List<String> parentIds = new ArrayList<>();
    List<Bundle> optionsList = new ArrayList<>();
    List<List<MediaItem>> childrenList = new ArrayList<>();
    Bundle requestNotifyChildrenWithDelayBundle =
        createNotifyChildrenChangedBundle(
            testParentId,
            /* itemCount= */ Integer.MAX_VALUE,
            /* delayMs= */ 100L,
            /* broadcast= */ false);
    requestNotifyChildrenWithDelayBundle.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, 12);

    browserCompat.subscribe(
        testParentId,
        requestNotifyChildrenWithDelayBundle,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
            parentIds.add(parentId);
            childrenList.add(children);
            optionsList.add(options);
            latch.countDown();
          }

          @Override
          public void onError(String parentId, Bundle options) {
            parentIds.add(parentId);
            optionsList.add(options);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(testParentId, testParentId);
    assertThat(childrenList).hasSize(1);
    assertThat(childrenList.get(0)).hasSize(12);
    assertThat(optionsList).hasSize(2);
    assertThat(optionsList.get(0).getString(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID))
        .isEqualTo(PARENT_ID_ALLOW_FIRST_ON_GET_CHILDREN);
    assertThat(optionsList.get(1).getString(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID))
        .isEqualTo(PARENT_ID_ALLOW_FIRST_ON_GET_CHILDREN);
  }

  @Test
  public void getChildren_broadcastNotifyChildrenChanged_callsOnChildrenLoadedTwice()
      throws Exception {
    String testParentId = SUBSCRIBE_PARENT_ID_2;
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(2);
    List<String> parentIds = new ArrayList<>();
    List<List<MediaItem>> childrenList = new ArrayList<>();
    Bundle requestNotifyChildrenWithDelayBundle =
        createNotifyChildrenChangedBundle(
            testParentId, /* itemCount= */ 12, /* delayMs= */ 100L, /* broadcast= */ true);
    requestNotifyChildrenWithDelayBundle.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, 12);

    browserCompat.subscribe(
        testParentId,
        requestNotifyChildrenWithDelayBundle,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
            parentIds.add(parentId);
            childrenList.add(children);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(testParentId, testParentId);
    assertThat(childrenList).hasSize(2);
    assertThat(childrenList.get(0)).hasSize(12);
    assertThat(childrenList.get(1)).hasSize(12);
  }

  @Test
  public void getChildren_commandGetChildrenNotAvailable_reportsError() throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN);
    handler.postAndSync(
        () -> {
          browserCompat =
              new MediaBrowserCompat(context, getServiceComponent(), connectionCallback, rootHints);
        });

    browserCompat.connect();

    assertThat(connectionCallback.connectedLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS))
        .isTrue();

    CountDownLatch errorLatch = new CountDownLatch(1);

    browserCompat.subscribe(
        PARENT_ID,
        new MediaBrowserCompat.SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            errorLatch.countDown();
          }
        });

    assertThat(errorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search() throws Exception {
    String testQuery = SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    AtomicReference<String> queryRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> itemsRef = new AtomicReference<>();
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
            queryRef.set(query);
            extrasRef.set(extras);
            itemsRef.set(items);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryRef.get()).isEqualTo(testQuery);
    assertThat(TestUtils.equals(testExtras, extrasRef.get())).isTrue();
    List<MediaItem> items = itemsRef.get();
    int expectedSize = Math.max(Math.min(pageSize, SEARCH_RESULT_COUNT - pageSize * page), 0);
    assertThat(items.size()).isEqualTo(expectedSize);
    int fromIndex = page * pageSize;
    int toIndex = Math.min((page + 1) * pageSize, SEARCH_RESULT_COUNT);
    // Compare the given results with originals.
    for (int originalIndex = fromIndex; originalIndex < toIndex; originalIndex++) {
      int relativeIndex = originalIndex - fromIndex;
      assertThat(items.get(relativeIndex).getMediaId()).isEqualTo(SEARCH_RESULT.get(originalIndex));
    }
  }

  @Test
  public void search_withLongList() throws Exception {
    String testQuery = SEARCH_QUERY_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    AtomicReference<String> queryRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> itemsRef = new AtomicReference<>();
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
            queryRef.set(query);
            extrasRef.set(extras);
            itemsRef.set(items);
            latch.countDown();
          }
        });

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryRef.get()).isEqualTo(testQuery);
    assertThat(TestUtils.equals(testExtras, extrasRef.get())).isTrue();
    List<MediaItem> items = itemsRef.get();
    assertThat(items).isNotNull();
    assertThat(items.size()).isLessThan(LONG_LIST_COUNT);
    for (int i = 0; i < items.size(); i++) {
      assertThat(items.get(i).getMediaId()).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void search_emptyResult() throws Exception {
    String testQuery = SEARCH_QUERY_EMPTY_RESULT;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    AtomicReference<String> queryRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicReference<List<MediaItem>> itemsRef = new AtomicReference<>();
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
            queryRef.set(query);
            extrasRef.set(extras);
            itemsRef.set(items);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryRef.get()).isEqualTo(testQuery);
    assertThat(TestUtils.equals(testExtras, extrasRef.get())).isTrue();
    List<MediaItem> items = itemsRef.get();
    assertThat(items).isNotNull();
    assertThat(items).isEmpty();
  }

  @Test
  public void search_commandSearchNotAvailable_reportsError() throws Exception {
    String testQuery = SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_SEARCH);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onError(String query, Bundle extras) {
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search_error() throws Exception {
    String testQuery = SEARCH_QUERY_ERROR;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> queryRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicBoolean onSearchResultCalled = new AtomicBoolean();

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onError(String query, Bundle extras) {
            queryRef.set(query);
            extrasRef.set(extras);
            latch.countDown();
          }

          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
            onSearchResultCalled.set(true);
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryRef.get()).isEqualTo(testQuery);
    assertThat(TestUtils.equals(testExtras, extrasRef.get())).isTrue();
    assertThat(onSearchResultCalled.get()).isFalse();
  }

  // TODO: Add test for onCustomCommand() in MediaLibrarySessionLegacyCallbackTest.
  @Test
  public void sendCustomAction() throws Exception {
    Bundle testArgs = new Bundle();
    testArgs.putString("args_key", "args_value");
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> actionRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicReference<Bundle> resultDataRef = new AtomicReference<>();

    browserCompat.sendCustomAction(
        CUSTOM_ACTION,
        testArgs,
        new CustomActionCallback() {
          @Override
          public void onResult(String action, Bundle extras, Bundle resultData) {
            actionRef.set(action);
            extrasRef.set(extras);
            resultDataRef.set(resultData);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(actionRef.get()).isEqualTo(CUSTOM_ACTION);
    assertThat(TestUtils.equals(testArgs, extrasRef.get())).isTrue();
    assertThat(TestUtils.equals(CUSTOM_ACTION_EXTRAS, resultDataRef.get())).isTrue();
  }

  @SuppressWarnings("deprecation") // test backwards compatibility
  @Test
  public void sendCustomAction_withProgressUpdates() throws Exception {
    Bundle testArgs = new Bundle();
    testArgs.putString("args_key", "args_value");
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<String> actionRef = new AtomicReference<>();
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    AtomicReference<Bundle> resultDataRef = new AtomicReference<>();
    List<String> progressUpdateActions = new ArrayList<>();
    List<Bundle> progressUpdateExtras = new ArrayList<>();
    List<Bundle> progressUpdateData = new ArrayList<>();

    browserCompat.sendCustomAction(
        MediaConstants.CUSTOM_COMMAND_DOWNLOAD,
        testArgs,
        new CustomActionCallback() {
          @Override
          public void onResult(String action, Bundle extras, Bundle resultData) {
            actionRef.set(action);
            extrasRef.set(extras);
            resultDataRef.set(resultData);
            latch.countDown();
          }

          @Override
          public void onProgressUpdate(String action, Bundle extras, Bundle data) {
            progressUpdateActions.add(action);
            progressUpdateExtras.add(extras);
            progressUpdateData.add(data);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(actionRef.get()).isEqualTo(MediaConstants.CUSTOM_COMMAND_DOWNLOAD);
    assertThat(TestUtils.equals(testArgs, extrasRef.get())).isTrue();
    assertThat(TestUtils.equals(CUSTOM_ACTION_EXTRAS, resultDataRef.get())).isTrue();
    assertThat(progressUpdateExtras).hasSize(2);
    assertThat(TestUtils.equals(testArgs, progressUpdateExtras.get(0))).isTrue();
    assertThat(TestUtils.equals(testArgs, progressUpdateExtras.get(1))).isTrue();
    assertThat(progressUpdateData).hasSize(2);
    assertThat(progressUpdateData.get(0).getInt("percent")).isEqualTo(30);
    assertThat(progressUpdateData.get(1).getInt("percent")).isEqualTo(100);
    assertThat(progressUpdateData.get(0).getFloat(MediaConstants.EXTRAS_KEY_DOWNLOAD_PROGRESS))
        .isEqualTo(EXTRAS_VALUE_PARTIAL_PROGRESS);
    assertThat(progressUpdateData.get(1).getFloat(MediaConstants.EXTRAS_KEY_DOWNLOAD_PROGRESS))
        .isEqualTo(1.0f);
    assertThat(progressUpdateActions)
        .containsExactly(
            MediaConstants.CUSTOM_COMMAND_DOWNLOAD, MediaConstants.CUSTOM_COMMAND_DOWNLOAD);
  }

  // TODO: Add test for onCustomCommand() in MediaLibrarySessionLegacyCallbackTest.
  @Test
  public void customAction_rejected() throws Exception {
    // This action will not be allowed by the library session.
    String testAction = "random_custom_action";
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.sendCustomAction(
        testAction,
        null,
        new CustomActionCallback() {
          @Override
          public void onResult(String action, Bundle extras, Bundle resultData) {
            latch.countDown();
          }
        });

    assertWithMessage("BrowserCompat shouldn't receive custom command")
        .that(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS))
        .isFalse();
  }

  @Test
  public void rootBrowserHints_usedAsConnectionHints() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putString(CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT, "myLibraryRoot");
    connectAndWait(connectionHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo("myLibraryRoot");
  }

  @Test
  public void rootBrowserHints_searchSupported_reportsSearchSupported() throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);

    boolean isSearchSupported =
        browserCompat.getExtras().getBoolean(BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED);

    assertThat(isSearchSupported).isTrue();
  }

  @Test
  public void rootBrowserHints_searchNotSupported_reportsSearchNotSupported() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_SEARCH);
    connectAndWait(connectionHints);

    boolean isSearchSupported =
        browserCompat.getExtras().getBoolean(BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED);

    assertThat(isSearchSupported).isFalse();
  }

  @Test
  public void rootBrowserHints_legacyBrowsableFlagSet_receivesRootWithBrowsableChildrenOnly()
      throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
        MediaItem.FLAG_BROWSABLE);
    connectAndWait(rootHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo(ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY);
  }

  @Test
  public void rootBrowserHints_legacyPlayableFlagSet_receivesDefaultRoot() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
        MediaItem.FLAG_BROWSABLE | MediaItem.FLAG_PLAYABLE);
    connectAndWait(connectionHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo(ROOT_ID);
  }
}
