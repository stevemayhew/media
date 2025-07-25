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
package androidx.media3.exoplayer.hls.playlist;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.max;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.hls.HlsDataSourceFactory;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.Part;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.RenditionReport;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.Segment;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist.Variant;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.CmcdData;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import androidx.media3.exoplayer.upstream.Loader;
import androidx.media3.exoplayer.upstream.Loader.LoadErrorAction;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import androidx.media3.exoplayer.util.ReleasableExecutor;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Default implementation for {@link HlsPlaylistTracker}. */
@UnstableApi
public final class DefaultHlsPlaylistTracker
    implements HlsPlaylistTracker, Loader.Callback<ParsingLoadable<HlsPlaylist>> {

  /** Factory for {@link DefaultHlsPlaylistTracker} instances. */
  public static final Factory FACTORY = DefaultHlsPlaylistTracker::new;

  /**
   * Default coefficient applied on the target duration of a playlist to determine the amount of
   * time after which an unchanging playlist is considered stuck.
   */
  public static final double DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 3.5;

  private final HlsDataSourceFactory dataSourceFactory;
  private final HlsPlaylistParserFactory playlistParserFactory;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final HashMap<Uri, MediaPlaylistBundle> playlistBundles;
  private final CopyOnWriteArrayList<PlaylistEventListener> listeners;
  private final double playlistStuckTargetDurationCoefficient;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  @Nullable private final Supplier<ReleasableExecutor> downloadExecutorSupplier;

  @Nullable private EventDispatcher eventDispatcher;
  @Nullable private Loader initialPlaylistLoader;
  @Nullable private Handler playlistRefreshHandler;
  @Nullable private PrimaryPlaylistListener primaryPlaylistListener;
  @Nullable private HlsMultivariantPlaylist multivariantPlaylist;
  @Nullable private Uri primaryMediaPlaylistUrl;
  @Nullable private HlsMediaPlaylist primaryMediaPlaylistSnapshot;
  private boolean isLive;
  private long initialStartTimeUs;

  /**
   * Creates an instance.
   *
   * @param dataSourceFactory A factory for {@link DataSource} instances.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
   * @param cmcdConfiguration The {@link CmcdConfiguration}.
   * @param downloadExecutorSupplier A supplier for a {@link ReleasableExecutor} that is used for
   *     loading the playlist.
   */
  public DefaultHlsPlaylistTracker(
      HlsDataSourceFactory dataSourceFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistParserFactory playlistParserFactory,
      @Nullable CmcdConfiguration cmcdConfiguration,
      @Nullable Supplier<ReleasableExecutor> downloadExecutorSupplier) {
    this(
        dataSourceFactory,
        loadErrorHandlingPolicy,
        playlistParserFactory,
        cmcdConfiguration,
        DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT,
        downloadExecutorSupplier);
  }

  /**
   * Creates an instance.
   *
   * @param dataSourceFactory A factory for {@link DataSource} instances.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
   * @param cmcdConfiguration The {@link CmcdConfiguration}.
   * @param playlistStuckTargetDurationCoefficient A coefficient to apply to the target duration of
   *     media playlists in order to determine that a non-changing playlist is stuck. Once a
   *     playlist is deemed stuck, a {@link PlaylistStuckException} is thrown via {@link
   *     #maybeThrowPlaylistRefreshError(Uri)}.
   * @param downloadExecutorSupplier A supplier for a {@link ReleasableExecutor} that is used for
   *     loading the playlist.
   */
  public DefaultHlsPlaylistTracker(
      HlsDataSourceFactory dataSourceFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistParserFactory playlistParserFactory,
      @Nullable CmcdConfiguration cmcdConfiguration,
      double playlistStuckTargetDurationCoefficient,
      @Nullable Supplier<ReleasableExecutor> downloadExecutorSupplier) {
    this.dataSourceFactory = dataSourceFactory;
    this.playlistParserFactory = playlistParserFactory;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.cmcdConfiguration = cmcdConfiguration;
    this.playlistStuckTargetDurationCoefficient = playlistStuckTargetDurationCoefficient;
    this.downloadExecutorSupplier = downloadExecutorSupplier;
    listeners = new CopyOnWriteArrayList<>();
    playlistBundles = new HashMap<>();
    initialStartTimeUs = C.TIME_UNSET;
  }

  // HlsPlaylistTracker implementation.

  @Override
  public void start(
      Uri initialPlaylistUri,
      EventDispatcher eventDispatcher,
      PrimaryPlaylistListener primaryPlaylistListener) {
    this.playlistRefreshHandler = Util.createHandlerForCurrentLooper();
    this.eventDispatcher = eventDispatcher;
    this.primaryPlaylistListener = primaryPlaylistListener;
    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(initialPlaylistUri)
            .setFlags(DataSpec.FLAG_ALLOW_GZIP)
            .build();
    if (cmcdConfiguration != null) {
      CmcdData cmcdData =
          new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_HLS)
              .setObjectType(CmcdData.OBJECT_TYPE_MANIFEST)
              .createCmcdData();
      dataSpec = cmcdData.addToDataSpec(dataSpec);
    }
    ParsingLoadable<HlsPlaylist> multivariantPlaylistLoadable =
        new ParsingLoadable<>(
            dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST),
            dataSpec,
            C.DATA_TYPE_MANIFEST,
            playlistParserFactory.createPlaylistParser());
    Assertions.checkState(initialPlaylistLoader == null);
    initialPlaylistLoader =
        downloadExecutorSupplier != null
            ? new Loader(downloadExecutorSupplier.get())
            : new Loader("DefaultHlsPlaylistTracker:MultivariantPlaylist");
    initialPlaylistLoader.startLoading(
        multivariantPlaylistLoadable,
        this,
        loadErrorHandlingPolicy.getMinimumLoadableRetryCount(multivariantPlaylistLoadable.type));
  }

  @Override
  public void stop() {
    primaryMediaPlaylistUrl = null;
    primaryMediaPlaylistSnapshot = null;
    multivariantPlaylist = null;
    initialStartTimeUs = C.TIME_UNSET;
    initialPlaylistLoader.release();
    initialPlaylistLoader = null;
    for (MediaPlaylistBundle bundle : playlistBundles.values()) {
      bundle.release();
    }
    playlistRefreshHandler.removeCallbacksAndMessages(null);
    playlistRefreshHandler = null;
    playlistBundles.clear();
  }

  @Override
  public void addListener(PlaylistEventListener listener) {
    checkNotNull(listener);
    listeners.add(listener);
  }

  @Override
  public void removeListener(PlaylistEventListener listener) {
    listeners.remove(listener);
  }

  @Override
  @Nullable
  public HlsMultivariantPlaylist getMultivariantPlaylist() {
    return multivariantPlaylist;
  }

  @Override
  @Nullable
  public HlsMediaPlaylist getPlaylistSnapshot(Uri url, boolean isForPlayback) {
    MediaPlaylistBundle bundle = playlistBundles.get(url);
    @Nullable HlsMediaPlaylist snapshot = bundle.getPlaylistSnapshot();
    if (snapshot != null && isForPlayback) {
      maybeSetPrimaryUrl(url);
      maybeActivateForPlayback(url);
    }
    return snapshot;
  }

  @Override
  public long getInitialStartTimeUs() {
    return initialStartTimeUs;
  }

  @Override
  public boolean isSnapshotValid(Uri url) {
    return playlistBundles.get(url).isSnapshotValid();
  }

  @Override
  public void maybeThrowPrimaryPlaylistRefreshError() throws IOException {
    if (initialPlaylistLoader != null) {
      initialPlaylistLoader.maybeThrowError();
    }
    if (primaryMediaPlaylistUrl != null) {
      maybeThrowPlaylistRefreshError(primaryMediaPlaylistUrl);
    }
  }

  @Override
  public void maybeThrowPlaylistRefreshError(Uri url) throws IOException {
    playlistBundles.get(url).maybeThrowPlaylistRefreshError();
  }

  @Override
  public void refreshPlaylist(Uri url) {
    playlistBundles.get(url).loadPlaylist(/* allowDeliveryDirectives= */ true);
  }

  @Override
  public boolean isLive() {
    return isLive;
  }

  @Override
  public boolean excludeMediaPlaylist(Uri playlistUrl, long exclusionDurationMs) {
    @Nullable MediaPlaylistBundle bundle = playlistBundles.get(playlistUrl);
    if (bundle != null) {
      return !bundle.excludePlaylist(exclusionDurationMs);
    }
    return false;
  }

  @Override
  public void deactivatePlaylistForPlayback(Uri url) {
    @Nullable MediaPlaylistBundle bundle = playlistBundles.get(url);
    if (bundle != null) {
      bundle.setActiveForPlayback(false);
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadStarted(
      ParsingLoadable<HlsPlaylist> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      int retryCount) {
    LoadEventInfo loadEventInfo =
        retryCount == 0
            ? new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs)
            : new LoadEventInfo(
                loadable.loadTaskId,
                loadable.dataSpec,
                loadable.getUri(),
                loadable.getResponseHeaders(),
                elapsedRealtimeMs,
                loadDurationMs,
                loadable.bytesLoaded());
    eventDispatcher.loadStarted(loadEventInfo, loadable.type, retryCount);
  }

  @Override
  public void onLoadCompleted(
      ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs, long loadDurationMs) {
    HlsPlaylist result = loadable.getResult();
    HlsMultivariantPlaylist multivariantPlaylist;
    boolean isMediaPlaylist = result instanceof HlsMediaPlaylist;
    if (isMediaPlaylist) {
      multivariantPlaylist =
          HlsMultivariantPlaylist.createSingleVariantMultivariantPlaylist(result.baseUri);
    } else /* result instanceof HlsMultivariantPlaylist */ {
      multivariantPlaylist = (HlsMultivariantPlaylist) result;
    }
    this.multivariantPlaylist = multivariantPlaylist;
    primaryMediaPlaylistUrl = multivariantPlaylist.variants.get(0).url;
    // Add a temporary playlist listener for loading the first primary playlist.
    listeners.add(new FirstPrimaryMediaPlaylistListener());
    createBundles(multivariantPlaylist.mediaPlaylistUrls);
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    MediaPlaylistBundle primaryBundle = playlistBundles.get(primaryMediaPlaylistUrl);
    if (isMediaPlaylist) {
      // We don't need to load the playlist again. We can use the same result.
      primaryBundle.processLoadedPlaylist((HlsMediaPlaylist) result, loadEventInfo);
    } else {
      primaryBundle.loadPlaylist(/* allowDeliveryDirectives= */ false);
    }
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    eventDispatcher.loadCompleted(loadEventInfo, C.DATA_TYPE_MANIFEST);
  }

  @Override
  public void onLoadCanceled(
      ParsingLoadable<HlsPlaylist> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      boolean released) {
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    eventDispatcher.loadCanceled(loadEventInfo, C.DATA_TYPE_MANIFEST);
  }

  @Override
  public LoadErrorAction onLoadError(
      ParsingLoadable<HlsPlaylist> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
    long retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount));
    boolean isFatal = retryDelayMs == C.TIME_UNSET;
    eventDispatcher.loadError(loadEventInfo, loadable.type, error, isFatal);
    if (isFatal) {
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }
    return isFatal
        ? Loader.DONT_RETRY_FATAL
        : Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs);
  }

  // Internal methods.

  private boolean maybeSelectNewPrimaryUrl() {
    List<Variant> variants = multivariantPlaylist.variants;
    int variantsSize = variants.size();
    long currentTimeMs = SystemClock.elapsedRealtime();
    for (int i = 0; i < variantsSize; i++) {
      MediaPlaylistBundle bundle = checkNotNull(playlistBundles.get(variants.get(i).url));
      if (currentTimeMs > bundle.excludeUntilMs) {
        primaryMediaPlaylistUrl = bundle.playlistUrl;
        bundle.loadPlaylistInternal(getRequestUriForPrimaryChange(primaryMediaPlaylistUrl));
        return true;
      }
    }
    return false;
  }

  private void maybeSetPrimaryUrl(Uri url) {
    if (url.equals(primaryMediaPlaylistUrl)
        || !isVariantUrl(url)
        || (primaryMediaPlaylistSnapshot != null && primaryMediaPlaylistSnapshot.hasEndTag)) {
      // Ignore if the primary media playlist URL is unchanged, if the media playlist is not
      // referenced directly by a variant, or if the last primary snapshot contains an end tag.
      return;
    }
    primaryMediaPlaylistUrl = url;
    MediaPlaylistBundle newPrimaryBundle = playlistBundles.get(primaryMediaPlaylistUrl);
    @Nullable HlsMediaPlaylist newPrimarySnapshot = newPrimaryBundle.playlistSnapshot;
    if (newPrimarySnapshot != null && newPrimarySnapshot.hasEndTag) {
      primaryMediaPlaylistSnapshot = newPrimarySnapshot;
      primaryPlaylistListener.onPrimaryPlaylistRefreshed(newPrimarySnapshot);
    } else {
      // The snapshot for the new primary media playlist URL may be stale. Defer updating the
      // primary snapshot until after we've refreshed it.
      newPrimaryBundle.loadPlaylistInternal(getRequestUriForPrimaryChange(url));
    }
  }

  private void maybeActivateForPlayback(Uri url) {
    MediaPlaylistBundle playlistBundle = playlistBundles.get(url);
    @Nullable HlsMediaPlaylist playlistSnapshot = playlistBundle.getPlaylistSnapshot();
    if (playlistBundle.isActiveForPlayback()) {
      return;
    }
    playlistBundle.setActiveForPlayback(true);
    if (playlistSnapshot != null && !playlistSnapshot.hasEndTag) {
      // For playlist that doesn't contain an end tag, we should trigger another load for it, as
      // the snapshot for it may be stale and it can keep refreshing as an active playlist.
      playlistBundle.loadPlaylist(true);
    }
  }

  private Uri getRequestUriForPrimaryChange(Uri newPrimaryPlaylistUri) {
    if (primaryMediaPlaylistSnapshot != null
        && primaryMediaPlaylistSnapshot.serverControl.canBlockReload) {
      @Nullable
      RenditionReport renditionReport =
          primaryMediaPlaylistSnapshot.renditionReports.get(newPrimaryPlaylistUri);
      if (renditionReport != null) {
        Uri.Builder uriBuilder = newPrimaryPlaylistUri.buildUpon();
        uriBuilder.appendQueryParameter(
            MediaPlaylistBundle.BLOCK_MSN_PARAM, String.valueOf(renditionReport.lastMediaSequence));
        if (renditionReport.lastPartIndex != C.INDEX_UNSET) {
          uriBuilder.appendQueryParameter(
              MediaPlaylistBundle.BLOCK_PART_PARAM, String.valueOf(renditionReport.lastPartIndex));
        }
        return uriBuilder.build();
      }
    }
    return newPrimaryPlaylistUri;
  }

  /**
   * Returns whether any of the variants in the multivariant playlist have the specified playlist
   * URL.
   */
  private boolean isVariantUrl(Uri playlistUrl) {
    List<Variant> variants = multivariantPlaylist.variants;
    for (int i = 0; i < variants.size(); i++) {
      if (playlistUrl.equals(variants.get(i).url)) {
        return true;
      }
    }
    return false;
  }

  private void createBundles(List<Uri> urls) {
    int listSize = urls.size();
    for (int i = 0; i < listSize; i++) {
      Uri url = urls.get(i);
      MediaPlaylistBundle bundle = new MediaPlaylistBundle(url);
      playlistBundles.put(url, bundle);
    }
  }

  /**
   * Called by the bundles when a snapshot changes.
   *
   * @param url The url of the playlist.
   * @param newSnapshot The new snapshot.
   */
  private void onPlaylistUpdated(Uri url, HlsMediaPlaylist newSnapshot) {
    if (url.equals(primaryMediaPlaylistUrl)) {
      if (primaryMediaPlaylistSnapshot == null) {
        // This is the first primary url snapshot.
        isLive = !newSnapshot.hasEndTag;
        initialStartTimeUs = newSnapshot.startTimeUs;
      }
      primaryMediaPlaylistSnapshot = newSnapshot;
      primaryPlaylistListener.onPrimaryPlaylistRefreshed(newSnapshot);
    }
    for (PlaylistEventListener listener : listeners) {
      listener.onPlaylistChanged();
    }
  }

  private boolean notifyPlaylistError(
      Uri playlistUrl, LoadErrorInfo loadErrorInfo, boolean forceRetry) {
    boolean anyExclusionFailed = false;
    for (PlaylistEventListener listener : listeners) {
      anyExclusionFailed |= !listener.onPlaylistError(playlistUrl, loadErrorInfo, forceRetry);
    }
    return anyExclusionFailed;
  }

  private HlsMediaPlaylist getLatestPlaylistSnapshot(
      @Nullable HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (!loadedPlaylist.isNewerThan(oldPlaylist)) {
      if (loadedPlaylist.hasEndTag) {
        // If the loaded playlist has an end tag but is not newer than the old playlist then we have
        // an inconsistent state. This is typically caused by the server incorrectly resetting the
        // media sequence when appending the end tag. We resolve this case as best we can by
        // returning the old playlist with the end tag appended.
        return oldPlaylist.copyWithEndTag();
      } else {
        return oldPlaylist;
      }
    }
    long startTimeUs = getLoadedPlaylistStartTimeUs(oldPlaylist, loadedPlaylist);
    int discontinuitySequence = getLoadedPlaylistDiscontinuitySequence(oldPlaylist, loadedPlaylist);
    return loadedPlaylist.copyWith(startTimeUs, discontinuitySequence);
  }

  private long getLoadedPlaylistStartTimeUs(
      @Nullable HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasProgramDateTime) {
      return loadedPlaylist.startTimeUs;
    }
    long primarySnapshotStartTimeUs =
        primaryMediaPlaylistSnapshot != null ? primaryMediaPlaylistSnapshot.startTimeUs : 0;
    if (oldPlaylist == null) {
      return primarySnapshotStartTimeUs;
    }
    int oldPlaylistSize = oldPlaylist.segments.size();
    Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist, loadedPlaylist);
    if (firstOldOverlappingSegment != null) {
      return oldPlaylist.startTimeUs + firstOldOverlappingSegment.relativeStartTimeUs;
    } else if (oldPlaylistSize == loadedPlaylist.mediaSequence - oldPlaylist.mediaSequence) {
      return oldPlaylist.getEndTimeUs();
    } else {
      // No segments overlap, we assume the new playlist start coincides with the primary playlist.
      return primarySnapshotStartTimeUs;
    }
  }

  private int getLoadedPlaylistDiscontinuitySequence(
      @Nullable HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasDiscontinuitySequence) {
      return loadedPlaylist.discontinuitySequence;
    }
    // TODO: Improve cross-playlist discontinuity adjustment.
    int primaryUrlDiscontinuitySequence =
        primaryMediaPlaylistSnapshot != null
            ? primaryMediaPlaylistSnapshot.discontinuitySequence
            : 0;
    if (oldPlaylist == null) {
      return primaryUrlDiscontinuitySequence;
    }
    Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist, loadedPlaylist);
    if (firstOldOverlappingSegment != null) {
      return oldPlaylist.discontinuitySequence
          + firstOldOverlappingSegment.relativeDiscontinuitySequence
          - loadedPlaylist.segments.get(0).relativeDiscontinuitySequence;
    }
    return primaryUrlDiscontinuitySequence;
  }

  private static Segment getFirstOldOverlappingSegment(
      HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    int mediaSequenceOffset = (int) (loadedPlaylist.mediaSequence - oldPlaylist.mediaSequence);
    List<Segment> oldSegments = oldPlaylist.segments;
    return mediaSequenceOffset < oldSegments.size() ? oldSegments.get(mediaSequenceOffset) : null;
  }

  /** Holds all information related to a specific Media Playlist. */
  private final class MediaPlaylistBundle implements Loader.Callback<ParsingLoadable<HlsPlaylist>> {

    private static final String BLOCK_MSN_PARAM = "_HLS_msn";
    private static final String BLOCK_PART_PARAM = "_HLS_part";
    private static final String SKIP_PARAM = "_HLS_skip";

    private final Uri playlistUrl;
    private final Loader mediaPlaylistLoader;
    private final DataSource mediaPlaylistDataSource;

    @Nullable private HlsMediaPlaylist playlistSnapshot;
    private long lastSnapshotLoadMs;
    private long lastSnapshotChangeMs;
    private long earliestNextLoadTimeMs;
    private long excludeUntilMs;
    private boolean loadPending;
    @Nullable private IOException playlistError;
    private boolean activeForPlayback;

    public MediaPlaylistBundle(Uri playlistUrl) {
      this.playlistUrl = playlistUrl;
      mediaPlaylistLoader =
          downloadExecutorSupplier != null
              ? new Loader(downloadExecutorSupplier.get())
              : new Loader("DefaultHlsPlaylistTracker:MediaPlaylist");
      mediaPlaylistDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST);
    }

    @Nullable
    public HlsMediaPlaylist getPlaylistSnapshot() {
      return playlistSnapshot;
    }

    public boolean isSnapshotValid() {
      if (playlistSnapshot == null) {
        return false;
      }
      long currentTimeMs = SystemClock.elapsedRealtime();
      long snapshotValidityDurationMs = max(30000, Util.usToMs(playlistSnapshot.durationUs));
      return playlistSnapshot.hasEndTag
          || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
          || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
          || lastSnapshotLoadMs + snapshotValidityDurationMs > currentTimeMs;
    }

    public void loadPlaylist(boolean allowDeliveryDirectives) {
      loadPlaylistInternal(allowDeliveryDirectives ? getMediaPlaylistUriForReload() : playlistUrl);
    }

    public void maybeThrowPlaylistRefreshError() throws IOException {
      mediaPlaylistLoader.maybeThrowError();
      if (playlistError != null) {
        throw playlistError;
      }
    }

    public boolean isActiveForPlayback() {
      return activeForPlayback;
    }

    public void setActiveForPlayback(boolean activeForPlayback) {
      this.activeForPlayback = activeForPlayback;
    }

    public void release() {
      mediaPlaylistLoader.release();
    }

    // Loader.Callback implementation.

    @Override
    public void onLoadStarted(
        ParsingLoadable<HlsPlaylist> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        int retryCount) {
      LoadEventInfo loadEventInfo =
          retryCount == 0
              ? new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs)
              : new LoadEventInfo(
                  loadable.loadTaskId,
                  loadable.dataSpec,
                  loadable.getUri(),
                  loadable.getResponseHeaders(),
                  elapsedRealtimeMs,
                  loadDurationMs,
                  loadable.bytesLoaded());
      eventDispatcher.loadStarted(loadEventInfo, loadable.type, retryCount);
    }

    @Override
    public void onLoadCompleted(
        ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      @Nullable HlsPlaylist result = loadable.getResult();
      LoadEventInfo loadEventInfo =
          new LoadEventInfo(
              loadable.loadTaskId,
              loadable.dataSpec,
              loadable.getUri(),
              loadable.getResponseHeaders(),
              elapsedRealtimeMs,
              loadDurationMs,
              loadable.bytesLoaded());
      if (result instanceof HlsMediaPlaylist) {
        processLoadedPlaylist((HlsMediaPlaylist) result, loadEventInfo);
        eventDispatcher.loadCompleted(loadEventInfo, C.DATA_TYPE_MANIFEST);
      } else {
        playlistError =
            ParserException.createForMalformedManifest(
                "Loaded playlist has unexpected type.", /* cause= */ null);
        eventDispatcher.loadError(
            loadEventInfo, C.DATA_TYPE_MANIFEST, playlistError, /* wasCanceled= */ true);
      }
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<HlsPlaylist> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      LoadEventInfo loadEventInfo =
          new LoadEventInfo(
              loadable.loadTaskId,
              loadable.dataSpec,
              loadable.getUri(),
              loadable.getResponseHeaders(),
              elapsedRealtimeMs,
              loadDurationMs,
              loadable.bytesLoaded());
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
      eventDispatcher.loadCanceled(loadEventInfo, C.DATA_TYPE_MANIFEST);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<HlsPlaylist> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      LoadEventInfo loadEventInfo =
          new LoadEventInfo(
              loadable.loadTaskId,
              loadable.dataSpec,
              loadable.getUri(),
              loadable.getResponseHeaders(),
              elapsedRealtimeMs,
              loadDurationMs,
              loadable.bytesLoaded());
      boolean isBlockingRequest = loadable.getUri().getQueryParameter(BLOCK_MSN_PARAM) != null;
      boolean deltaUpdateFailed = error instanceof HlsPlaylistParser.DeltaUpdateException;
      if (isBlockingRequest || deltaUpdateFailed) {
        int responseCode = Integer.MAX_VALUE;
        if (error instanceof HttpDataSource.InvalidResponseCodeException) {
          responseCode = ((HttpDataSource.InvalidResponseCodeException) error).responseCode;
        }
        if (deltaUpdateFailed || responseCode == 400 || responseCode == 503) {
          // Intercept failed delta updates and blocking requests producing a Bad Request (400) and
          // Service Unavailable (503). In such cases, force a full, non-blocking request (see RFC
          // 8216, section 6.2.5.2 and 6.3.7).
          earliestNextLoadTimeMs = SystemClock.elapsedRealtime();
          loadPlaylist(/* allowDeliveryDirectives= */ false);
          castNonNull(eventDispatcher)
              .loadError(loadEventInfo, loadable.type, error, /* wasCanceled= */ true);
          return Loader.DONT_RETRY;
        }
      }
      MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
      LoadErrorInfo loadErrorInfo =
          new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);
      boolean exclusionFailed =
          notifyPlaylistError(playlistUrl, loadErrorInfo, /* forceRetry= */ false);
      LoadErrorAction loadErrorAction;
      if (exclusionFailed) {
        long retryDelay = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
        loadErrorAction =
            retryDelay != C.TIME_UNSET
                ? Loader.createRetryAction(false, retryDelay)
                : Loader.DONT_RETRY_FATAL;
      } else {
        loadErrorAction = Loader.DONT_RETRY;
      }

      boolean wasCanceled = !loadErrorAction.isRetry();
      eventDispatcher.loadError(loadEventInfo, loadable.type, error, wasCanceled);
      if (wasCanceled) {
        loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
      }
      return loadErrorAction;
    }

    // Internal methods.

    private void loadPlaylistInternal(Uri playlistRequestUri) {
      excludeUntilMs = 0;
      if (loadPending || mediaPlaylistLoader.isLoading() || mediaPlaylistLoader.hasFatalError()) {
        // Load already pending, in progress, or a fatal error has been encountered. Do nothing.
        return;
      }
      long currentTimeMs = SystemClock.elapsedRealtime();
      if (currentTimeMs < earliestNextLoadTimeMs) {
        loadPending = true;
        playlistRefreshHandler.postDelayed(
            () -> {
              loadPending = false;
              loadPlaylistImmediately(playlistRequestUri);
            },
            earliestNextLoadTimeMs - currentTimeMs);
      } else {
        loadPlaylistImmediately(playlistRequestUri);
      }
    }

    private void loadPlaylistImmediately(Uri playlistRequestUri) {
      ParsingLoadable.Parser<HlsPlaylist> mediaPlaylistParser =
          playlistParserFactory.createPlaylistParser(multivariantPlaylist, playlistSnapshot);
      DataSpec dataSpec =
          new DataSpec.Builder()
              .setUri(playlistRequestUri)
              .setFlags(DataSpec.FLAG_ALLOW_GZIP)
              .build();
      if (cmcdConfiguration != null) {
        CmcdData.Factory cmcdDataFactory =
            new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_HLS)
                .setObjectType(CmcdData.OBJECT_TYPE_MANIFEST);
        if (primaryMediaPlaylistSnapshot != null) {
          cmcdDataFactory.setIsLive(!primaryMediaPlaylistSnapshot.hasEndTag);
        }
        dataSpec = cmcdDataFactory.createCmcdData().addToDataSpec(dataSpec);
      }
      ParsingLoadable<HlsPlaylist> mediaPlaylistLoadable =
          new ParsingLoadable<>(
              mediaPlaylistDataSource, dataSpec, C.DATA_TYPE_MANIFEST, mediaPlaylistParser);
      mediaPlaylistLoader.startLoading(
          mediaPlaylistLoadable,
          /* callback= */ this,
          loadErrorHandlingPolicy.getMinimumLoadableRetryCount(mediaPlaylistLoadable.type));
    }

    private void processLoadedPlaylist(
        HlsMediaPlaylist loadedPlaylist, LoadEventInfo loadEventInfo) {
      @Nullable HlsMediaPlaylist oldPlaylist = playlistSnapshot;
      long currentTimeMs = SystemClock.elapsedRealtime();
      lastSnapshotLoadMs = currentTimeMs;
      playlistSnapshot = getLatestPlaylistSnapshot(oldPlaylist, loadedPlaylist);
      if (playlistSnapshot != oldPlaylist) {
        playlistError = null;
        lastSnapshotChangeMs = currentTimeMs;
        onPlaylistUpdated(playlistUrl, playlistSnapshot);
      } else if (!playlistSnapshot.hasEndTag) {
        boolean forceRetry = false;
        @Nullable IOException playlistError = null;
        if (loadedPlaylist.mediaSequence + loadedPlaylist.segments.size()
            < playlistSnapshot.mediaSequence) {
          // TODO: Allow customization of playlist resets handling.
          // The media sequence jumped backwards. The server has probably reset. We do not try
          // excluding in this case.
          forceRetry = true;
          playlistError = new PlaylistResetException(playlistUrl);
        } else if (currentTimeMs - lastSnapshotChangeMs
            > Util.usToMs(playlistSnapshot.targetDurationUs)
                * playlistStuckTargetDurationCoefficient) {
          // TODO: Allow customization of stuck playlists handling.
          playlistError = new PlaylistStuckException(playlistUrl);
        }
        if (playlistError != null) {
          this.playlistError = playlistError;
          notifyPlaylistError(
              playlistUrl,
              new LoadErrorInfo(
                  loadEventInfo,
                  new MediaLoadData(C.DATA_TYPE_MANIFEST),
                  playlistError,
                  /* errorCount= */ 1),
              forceRetry);
        }
      }
      long durationUntilNextLoadUs = 0L;
      if (!playlistSnapshot.serverControl.canBlockReload) {
        // If blocking requests are not supported, do not allow the playlist to load again within
        // the target duration if we obtained a new snapshot, or half the target duration otherwise.
        durationUntilNextLoadUs =
            playlistSnapshot != oldPlaylist
                ? playlistSnapshot.targetDurationUs
                : (playlistSnapshot.targetDurationUs / 2);
      } else if (playlistSnapshot == oldPlaylist) {
        // To prevent infinite requests when the server responds with CAN-BLOCK-RELOAD=YES but does
        // not actually block until the playlist updates, wait for half the target duration before
        // retrying.
        durationUntilNextLoadUs =
            playlistSnapshot.partTargetDurationUs != C.TIME_UNSET
                ? playlistSnapshot.partTargetDurationUs / 2
                : playlistSnapshot.targetDurationUs / 2;
      }
      earliestNextLoadTimeMs =
          currentTimeMs + Util.usToMs(durationUntilNextLoadUs) - loadEventInfo.loadDurationMs;
      // Schedule a load if this is the primary or playback playlist and it doesn't have an end tag.
      // Else the next load will be scheduled when refreshPlaylist is called, or when this playlist
      // becomes the primary or active for playback.
      if (!playlistSnapshot.hasEndTag
          && (playlistUrl.equals(primaryMediaPlaylistUrl) || activeForPlayback)) {
        loadPlaylistInternal(getMediaPlaylistUriForReload());
      }
    }

    private Uri getMediaPlaylistUriForReload() {
      if (playlistSnapshot == null
          || (playlistSnapshot.serverControl.skipUntilUs == C.TIME_UNSET
              && !playlistSnapshot.serverControl.canBlockReload)) {
        return playlistUrl;
      }
      Uri.Builder uriBuilder = playlistUrl.buildUpon();
      if (playlistSnapshot.serverControl.canBlockReload) {
        long targetMediaSequence =
            playlistSnapshot.mediaSequence + playlistSnapshot.segments.size();
        uriBuilder.appendQueryParameter(BLOCK_MSN_PARAM, String.valueOf(targetMediaSequence));
        if (playlistSnapshot.partTargetDurationUs != C.TIME_UNSET) {
          List<Part> trailingParts = playlistSnapshot.trailingParts;
          int targetPartIndex = trailingParts.size();
          if (!trailingParts.isEmpty() && Iterables.getLast(trailingParts).isPreload) {
            // Ignore the preload part.
            targetPartIndex--;
          }
          uriBuilder.appendQueryParameter(BLOCK_PART_PARAM, String.valueOf(targetPartIndex));
        }
      }
      if (playlistSnapshot.serverControl.skipUntilUs != C.TIME_UNSET) {
        uriBuilder.appendQueryParameter(
            SKIP_PARAM, playlistSnapshot.serverControl.canSkipDateRanges ? "v2" : "YES");
      }
      return uriBuilder.build();
    }

    /**
     * Excludes the playlist.
     *
     * @param exclusionDurationMs The number of milliseconds for which the playlist should be
     *     excluded.
     * @return Whether the playlist is the primary, despite being excluded.
     */
    private boolean excludePlaylist(long exclusionDurationMs) {
      excludeUntilMs = SystemClock.elapsedRealtime() + exclusionDurationMs;
      return playlistUrl.equals(primaryMediaPlaylistUrl) && !maybeSelectNewPrimaryUrl();
    }
  }

  /**
   * Takes care of handling load errors of the first media playlist and applies exclusion according
   * to the {@link LoadErrorHandlingPolicy} before the first media period has been created and
   * prepared.
   */
  private class FirstPrimaryMediaPlaylistListener implements PlaylistEventListener {

    @Override
    public void onPlaylistChanged() {
      // Remove the temporary playlist listener that is waiting for the first playlist only.
      listeners.remove(this);
    }

    @Override
    public boolean onPlaylistError(Uri url, LoadErrorInfo loadErrorInfo, boolean forceRetry) {
      if (primaryMediaPlaylistSnapshot == null) {
        long nowMs = SystemClock.elapsedRealtime();
        int variantExclusionCounter = 0;
        List<Variant> variants = castNonNull(multivariantPlaylist).variants;
        for (int i = 0; i < variants.size(); i++) {
          @Nullable
          MediaPlaylistBundle mediaPlaylistBundle = playlistBundles.get(variants.get(i).url);
          if (mediaPlaylistBundle != null && nowMs < mediaPlaylistBundle.excludeUntilMs) {
            variantExclusionCounter++;
          }
        }
        LoadErrorHandlingPolicy.FallbackOptions fallbackOptions =
            new LoadErrorHandlingPolicy.FallbackOptions(
                /* numberOfLocations= */ 1,
                /* numberOfExcludedLocations= */ 0,
                /* numberOfTracks= */ multivariantPlaylist.variants.size(),
                /* numberOfExcludedTracks= */ variantExclusionCounter);
        @Nullable
        LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
            loadErrorHandlingPolicy.getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
        if (fallbackSelection != null
            && fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
          @Nullable MediaPlaylistBundle mediaPlaylistBundle = playlistBundles.get(url);
          if (mediaPlaylistBundle != null) {
            mediaPlaylistBundle.excludePlaylist(fallbackSelection.exclusionDurationMs);
          }
        }
      }
      return false;
    }
  }
}
