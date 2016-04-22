package com.daprlabs.cardstack;

import android.widget.BaseAdapter;

public abstract class SwipeDeckAdapter extends BaseAdapter {

    /**
     * Remove the top item from the dataset held by this adapter.
     */
    public abstract void removeCard(long cardId);
}
