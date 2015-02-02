package com.danilov.mangareader.core.strategy;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.danilov.mangareader.core.interfaces.MangaShowObserver;
import com.danilov.mangareader.core.interfaces.MangaShowStrategy;
import com.danilov.mangareader.core.model.Manga;
import com.danilov.mangareader.core.model.MangaChapter;
import com.danilov.mangareader.core.repository.RepositoryEngine;
import com.danilov.mangareader.core.repository.RepositoryException;
import com.danilov.mangareader.core.service.DownloadManager;
import com.danilov.mangareader.core.util.OldPromise;
import com.danilov.mangareader.core.util.Promise;
import com.danilov.mangareader.core.view.InAndOutAnim;
import com.danilov.mangareader.core.view.MangaImageSwitcher;

import java.io.File;
import java.util.List;

/**
 * Created by Semyon Danilov on 21.06.2014.
 *
 * This class handles showing pictures from web
 *
 */
public class OnlineManga implements MangaShowStrategy {

    private static final String TAG = "OnlineManga";

    private MangaImageSwitcher mangaImageSwitcher;
    private InAndOutAnim nextImageAnim;
    private InAndOutAnim prevImageAnim;

    private DownloadManager downloadManager;
    private Handler handler;

    private Manga manga;
    private RepositoryEngine engine;
    private int currentImageNumber = -1;
    private int currentChapter;
    private int totalImages = 0;
    private List<String> uris = null;

    private MangaShowObserver observer;
    private MangaStrategyListener listener;

    private boolean destroyed = false;

    public OnlineManga(final Manga manga, final MangaImageSwitcher mangaImageSwitcher, final InAndOutAnim nextImageAnim, final InAndOutAnim prevImageAnim) {
        this.manga = manga;
        this.engine = manga.getRepository().getEngine();
        this.mangaImageSwitcher = mangaImageSwitcher;
        this.nextImageAnim = nextImageAnim;
        this.prevImageAnim = prevImageAnim;
        this.handler = new Handler();
        this.downloadManager = new DownloadManager();
    }

    @Override
    public void restoreState(final List<String> uris, final int chapter, final int image) {
        this.currentChapter = chapter;
        this.savedCurrentImageNumber = image;
        this.uris = uris;
        if (uris != null) {
            this.totalImages = uris.size();
        }
    }

    @Override
    public void showImage(final int i) {
        if (i == currentImageNumber || i >= uris.size() || i < 0) {
            return;
        }
        listener.onImageLoadStart(this);
        String uri = uris.get(i);
        String path = Environment.getExternalStorageDirectory() + "/cache/";
        File f = new File(path);
        f.mkdirs();
        path += "1.png";
        downloadManager.cancelAllDownloads();
        downloadManager.setListener(new DownloadListener(path, i));
        downloadManager.startDownload(uri, path);
    }

    private class DownloadListener implements DownloadManager.DownloadProgressListener {

        private String path;
        private int imgNum;

        public DownloadListener(final String path, final int imgNum) {
            this.path = path;
            this.imgNum = imgNum;
        }

        @Override
        public void onProgress(final DownloadManager.Download download, final int progress) {
            int total = download.getSize();
            listener.onImageLoadProgress(OnlineManga.this, progress, total);
        }

        @Override
        public void onPause(final DownloadManager.Download download) {

        }

        @Override
        public void onResume(final DownloadManager.Download download) {

        }

        @Override
        public void onComplete(final DownloadManager.Download download) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) {
                        return;
                    }
                    listener.onImageLoadEnd(OnlineManga.this, true, "");
                    File imageFile = new File(path);
                    if (imgNum < currentImageNumber) {
                        mangaImageSwitcher.setInAndOutAnim(prevImageAnim);
                        mangaImageSwitcher.setPreviousImageDrawable(imageFile.getPath());
                    } else if (imgNum > currentImageNumber) {
                        mangaImageSwitcher.setInAndOutAnim(nextImageAnim);
                        mangaImageSwitcher.setNextImageDrawable(imageFile.getPath());
                    }
                    currentImageNumber = imgNum;
                    updateObserver();
                }
            });
        }

        @Override
        public void onCancel(final DownloadManager.Download download) {

        }

        @Override
        public void onError(final DownloadManager.Download download, final String errorMsg) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) {
                        return;
                    }
                    listener.onImageLoadEnd(OnlineManga.this, false, errorMsg);
                }
            });
        }

    }

    private void updateObserver() {
        if (observer != null) {
            observer.onUpdate(this);
        }
    }

    private int savedCurrentImageNumber = 0;

    @Override
    public Promise<Result> showChapter(final int i) throws ShowMangaException {
        this.currentChapter = i;
        this.savedCurrentImageNumber = this.currentImageNumber;
        this.currentImageNumber = -1;
        if (manga.getChaptersQuantity() <= 0) {
            throw new ShowMangaException("No chapters to show");
        }
        final MangaChapter chapter = manga.getChapterByNumber(i);
        if (chapter == null) {
            throw new ShowMangaException("No chapter ");
        }
        final Promise<Result> promise = new Promise<Result>();
        listener.onChapterInfoLoadStart(this);
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    uris = engine.getChapterImages(chapter);
                    totalImages = uris.size();
                } catch (Exception e) {
                    listener.onChapterInfoLoadEnd(OnlineManga.this, false, e.getMessage());
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        listener.onChapterInfoLoadEnd(OnlineManga.this, true, "");
                        if (uris != null && uris.size() > 0) {
                            //showImage(0);
                        }
                        updateObserver();
                        promise.finish(Result.SUCCESS, true);
                    }
                });
            }
        };
        thread.start();
        return promise;
    }

    @Override
    public Promise<Result> next() throws ShowMangaException {
        if (currentImageNumber + 1 >= uris.size()) {
            return showChapter(currentChapter + 1);
        }
        showImage(currentImageNumber + 1);
        return null;
    }

    @Override
    public void previous() throws ShowMangaException{
        showImage(currentImageNumber - 1);
    }

    @Override
    public Promise<Result> initStrategy() {
        final Promise<Result> promise = new Promise<Result>();
        if (manga.getChapters() != null) {
            promise.finish(Result.SUCCESS, true);
        } else {
            Thread t = new Thread() {

                @Override
                public void run() {
                    try {
                        engine.queryForChapters(manga);
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                if (destroyed) {
                                    return;
                                }
                                promise.finish(Result.SUCCESS, true);
                            }

                        });
                    } catch (RepositoryException e) {
                        Log.e(TAG, "Failed to load chapters: " + e.getMessage());
                        promise.finish(Result.ERROR, false);
                    }
                }

            };
            t.start();
        }
        return promise;
    }

    @Override
    public int getCurrentImageNumber() {
        if (currentImageNumber == -1) {
            return savedCurrentImageNumber;
        }
        return currentImageNumber;
    }

    @Override
    public int getTotalImageNumber() {
        return totalImages;
    }

    @Override
    public int getCurrentChapterNumber() {
        return currentChapter;
    }

    @Override
    public String getTotalChaptersNumber() {
        if (manga == null) {
            return "0";
        }
        return String.valueOf(manga.getChaptersQuantity());
    }

    @Override
    public List<String> getChapterUris() {
        return uris;
    }

    @Override
    public void setObserver(final MangaShowObserver observer) {
        this.observer = observer;
    }

    @Override
    public void setOnStrategyListener(final MangaStrategyListener mangaStrategyListener) {
        this.listener = mangaStrategyListener;
    }

    @Override
    public void destroy() {
        this.destroyed = true;
    }

}