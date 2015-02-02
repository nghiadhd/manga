package com.danilov.mangareader.fragment;

import android.support.v4.app.Fragment;
import android.view.View;

import com.danilov.mangareader.core.util.SafeHandler;

/**
 * Created by Semyon on 15.11.2014.
 */
public class BaseFragment extends Fragment {

    protected View view;

    protected SafeHandler handler = new SafeHandler();

    protected <T> T findViewById(final int id) {
        return (T) view.findViewById(id);
    }

}