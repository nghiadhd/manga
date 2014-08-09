package com.danilov.manga.core.strategy;

import com.danilov.manga.core.interfaces.MangaShowStrategy;

/**
 * Created by Semyon Danilov on 21.06.2014.
 */
public class OnlineManga implements MangaShowStrategy {

    @Override
    public void showImage(final int i) {
    }

    @Override
    public void showChapter(final int i) {

    }

    @Override
    public void next() {

    }

    @Override
    public void previous() {

    }

    @Override
    public void initStrategy() throws ShowMangaException {

    }

    @Override
    public int getCurrentImageNumber() {
        return 0;
    }

    @Override
    public int getCurrentChapterNumber() {
        return 0;
    }

}
