package com.daprlabs.cardstack;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.LinkedList;

public class SwipeDeck extends FrameLayout {

    private final static int ANIMATION_TIME = 160;

    private static int     NUMBER_OF_CARDS;
    private        float   ROTATION_DEGREES;
    private        float   CARD_SPACING;
    private        boolean RENDER_ABOVE;
    private        boolean RENDER_BELOW;
    private        float   OPACITY_END;
    private        int     paddingLeft;

    private boolean hardwareAccelerationEnabled = true;

    private int paddingRight;
    private int paddingTop;
    private int paddingBottom;

    private SwipeEventCallback eventCallback;

    /**
     * The adapter with all the data
     */
    private       SwipeDeckAdapter mAdapter;
    private       DataSetObserver  observer;
    private       int              currentCard;
    private final LinkedList<View> cards;
    private final Handler          handler;

    private int leftImageResource;
    private int rightImageResource;

    public SwipeDeck(Context context, AttributeSet attrs) {
        super(context, attrs);

        cards = new LinkedList<>();
        handler = new Handler();

        TypedArray a = context.getTheme()
                              .obtainStyledAttributes(
                                      attrs,
                                      R.styleable.SwipeDeck,
                                      0, 0);
        try {
            NUMBER_OF_CARDS = a.getInt(R.styleable.SwipeDeck_max_visible, 3);
            ROTATION_DEGREES = a.getFloat(R.styleable.SwipeDeck_rotation_degrees, 15f);
            CARD_SPACING = a.getDimension(R.styleable.SwipeDeck_card_spacing, 15f);
            RENDER_ABOVE = a.getBoolean(R.styleable.SwipeDeck_render_above, true);
            RENDER_BELOW = a.getBoolean(R.styleable.SwipeDeck_render_below, false);
            OPACITY_END = a.getFloat(R.styleable.SwipeDeck_opacity_end, 0.33f);
        } finally {
            a.recycle();
        }

        paddingBottom = getPaddingBottom();
        paddingLeft = getPaddingLeft();
        paddingRight = getPaddingRight();
        paddingTop = getPaddingTop();

        //set clipping of view parent to false so cards render outside their view boundary
        //make sure not to clip to padding
        setClipToPadding(false);
        setClipChildren(false);

        this.setWillNotDraw(false);

        //render the cards and card deck above or below everything
        if (RENDER_ABOVE) {
            ViewCompat.setTranslationZ(this, Float.MAX_VALUE);
        }
        if (RENDER_BELOW) {
            ViewCompat.setTranslationZ(this, Float.MIN_VALUE);
        }
    }

    public void setLeftImage(int imageResource) {
        leftImageResource = imageResource;
    }

    public void setRightImage(int imageResource) {
        rightImageResource = imageResource;
    }

    public void setEventCallback(SwipeEventCallback eventCallback) {
        this.eventCallback = eventCallback;
    }

    // ===== VIEW ==================================================================================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = widthSize;
        } else {
            //Be whatever you want
            width = widthSize;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = heightSize;
        } else {
            //Be whatever you want
            height = heightSize;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // if we don't have an adapter, we don't need to do anything
        if (mAdapter == null || mAdapter.getCount() == 0) {
            clearCards();
            removeAllViewsInLayout();
            return;
        }
        addAndPositionCards();
    }

    // ===== HARDWARE ACCELERATION =================================================================

    /**
     * Set Hardware Acceleration Enabled.
     *
     * @param acceleration
     */
    @SuppressWarnings("JavaDoc")
    public void setHardwareAccelerationEnabled(Boolean acceleration) {
        this.hardwareAccelerationEnabled = acceleration;
    }

    // ===== ADAPTER ===============================================================================

    public void setAdapter(SwipeDeckAdapter adapter) {
        if (this.mAdapter != null) {
            this.mAdapter.unregisterDataSetObserver(observer);
        }
        mAdapter = adapter;

        observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                //handle data set changes
                //if we need to add any cards at this point (ie. the amount of cards on screen
                //is less than the max number of cards to display) add the cards.
                addAndPositionCards();
            }

            @Override
            public void onInvalidated() {
                //reset state, remove views and request layout
                clearCards();
                removeAllViews();
                requestLayout();
            }
        };

        adapter.registerDataSetObserver(observer);
        removeAllViewsInLayout();
        requestLayout();
    }

    // ===== CARD MANIPULATION =====================================================================

    private void clearCards() {
        currentCard = 0;
        cards.clear();
    }

    // ----- SWIPING CARDS -------------------------------------------------------------------------

    public void swipeTopCardLeft(int duration) {
//        onSwipeTopCard(false, duration);
        removeAndAddNext(false);
    }

    public void swipeTopCardRight(int duration) {
//        onSwipeTopCard(true, duration);
        removeAndAddNext(true);
    }

    // ----- ADDING CARDS --------------------------------------------------------------------------

    private void addAndPositionCards() {
        // clear previous cards
        clearCards();
        // remove all previous views
        removeAllViews();
        // get all views from adapter

        layoutAndAddCard();
        for (int i = 0; i < getChildCount(); ++i) {
            positionItem(i);
        }
    }

    /**
     * Adds a view as a child view and takes care of measuring it
     */
    private void layoutAndAddCard() {
        final ViewGroup.LayoutParams[] paramMap = new ViewGroup.LayoutParams[NUMBER_OF_CARDS];
        View card;
        ViewGroup.LayoutParams params;
        cards.clear();

        for (int i = currentCard; i < mAdapter.getCount(); i++) {
            if (i == (NUMBER_OF_CARDS + currentCard)) {
                break;
            }
            card = mAdapter.getView(i, null/*lastRemovedView*/, this);
            if (hardwareAccelerationEnabled) {
                //set backed by an off-screen buffer
                card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            cards.push(card);
        }

        for (int i = 0; i < cards.size(); i++) {
            card = cards.get(i);
            params = card.getLayoutParams();
            if (params == null) {
                params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            }
            paramMap[i] = params;
            //ensure new card is under the deck at the beginning
            card.setY(paddingTop);
        }

        for (int i = 0; i < cards.size(); i++) {
            card = cards.get(i);
            addViewInLayout(card, -1, paramMap[i], true);
            int itemWidth = getWidth() - (paddingLeft + paddingRight);
            int itemHeight = getHeight() - (paddingTop + paddingBottom);
            card.measure(MeasureSpec.EXACTLY | itemWidth,
                         MeasureSpec.EXACTLY | itemHeight); //MeasureSpec.UNSPECIFIED

            //ensure that if there's a left and right image set their alpha to 0 initially
            //alpha animation is handled in the swipe listener
            if (leftImageResource != 0) {
                card.findViewById(leftImageResource)
                    .setAlpha(0);
            }
            if (rightImageResource != 0) {
                card.findViewById(rightImageResource)
                    .setAlpha(0);
            }
            // lets not set touch delegates on this view until we figure out
            // touch issues R.Pina 20160420
//            setTouchInteractions(card);
            setZTranslations();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setZTranslations() {
        //this is only needed to add shadows to cardviews on > lollipop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int count = getChildCount();
            for (int i = 0; i < count; ++i) {
                getChildAt(i).setTranslationZ(i * 10);
            }
        }
    }

    /**
     * Positions the children at the "correct" positions
     */
    private void positionItem(int index) {
        final View child = getChildAt(index);
        int width = child.getMeasuredWidth();
        int height = child.getMeasuredHeight();
        int left = (getWidth() - width) / 2;
        child.layout(left, paddingTop, left + width, paddingTop + height);
        //layout each child slightly above the previous child (we start with the bottom)
        int childCount = getChildCount();
        float offset = (int) (((childCount - 1) * CARD_SPACING) - (index * CARD_SPACING));
        child.animate()
             .setDuration(160)
             .y(paddingTop + offset);
    }


    private void setTouchInteractions(@NonNull View view) {
        //this calculation is to get the correct position in the adapter of the current top card
        //the card position on setup top card is currently always the bottom card in the view
        //at any given time.
        int initialX = paddingLeft;
        int initialY = paddingTop;

        final SwipeListener swipeListener = new SwipeListener(view,
                                                              getSwipeCallback(),
                                                              initialX,
                                                              initialY,
                                                              ROTATION_DEGREES,
                                                              OPACITY_END,
                                                              false);
        //if we specified these image resources, get the views and pass them to the swipe listener
        //for the sake of animating them
        View rightView = null;
        View leftView = null;
        if (!(rightImageResource == 0)) {
            rightView = view.findViewById(rightImageResource);
        }
        if (!(leftImageResource == 0)) {
            leftView = view.findViewById(leftImageResource);
        }
        swipeListener.setLeftView(leftView);
        swipeListener.setRightView(rightView);

        view.setOnTouchListener(swipeListener);
        view.setTag(swipeListener);
    }

    // ----- REMOVING CARDS ------------------------------------------------------------------------

    // TODO animate card when dismissing it R.Pina 20160419
    private void onSwipeTopCard(final boolean right, int duration) {
        final View child = getChildAt(getChildCount() - 1);
        final SwipeListener swipeListener = (SwipeListener) child.getTag();
        if (right) {
            swipeListener.animateOffScreenRight(duration);
        } else {
            swipeListener.animateOffScreenLeft(duration);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removeAndAddNext(right);
            }
        }, duration);
    }

    private void removeAndAddNext(boolean right) {
        if (eventCallback != null) {
            if (right) {
                eventCallback.cardSwipedRight();
            } else {
                eventCallback.cardSwipedLeft();
            }
        }
        mAdapter.removeTop();
        removeTopCard();
        currentCard++;
        layoutAndAddCard();
        setZTranslations();
    }

    // figure out touch issues R.Pina 20140420
    private void removeTopCard() {
        //top card is now the last in view children
        final View child = getChildAt(getChildCount() - 1);
//        if (child != null) {
//            child.setOnTouchListener(null);
//            child.setTag(null);
//            //this will also check to see if cards are depleted
//            removeViewWaitForAnimation(child);
//        }
        removeView(child);
        //if there are no more children left after top card removal let the callback know
        if (mAdapter.getCount() == 0) {
            eventCallback.cardsDepleted();
        }
    }

    // figure out touch issues R.Pina 20140420
//    private void removeViewWaitForAnimation(View child) {
//        new RemoveViewOnAnimCompleted().execute(child);
//    }

    private SwipeListener.SwipeCallback getSwipeCallback() {
        return new SwipeListener.SwipeCallback() {
            @Override
            public void cardSwipedLeft() {
                removeAndAddNext(false);
            }

            @Override
            public void cardSwipedRight() {
                removeAndAddNext(true);
            }

            @Override
            public void cardOffScreen() {
            }

            @Override
            public void cardActionDown() {
                if (eventCallback != null) {
                    eventCallback.cardActionDown();
                }
            }

            @Override
            public void cardActionUp() {
                if (eventCallback != null) {
                    eventCallback.cardActionUp();
                }
            }
        };
    }

    public interface SwipeEventCallback {
        //returning the object position in the adapter
        void cardSwipedLeft();

        void cardSwipedRight();

        void cardsDepleted();

        void cardActionDown();

        void cardActionUp();
    }

    private class RemoveViewOnAnimCompleted extends AsyncTask<View, Void, View> {

        @Override
        protected View doInBackground(View... params) {
            android.os.SystemClock.sleep(ANIMATION_TIME);
            return params[0];
        }

        @Override
        protected void onPostExecute(View view) {
            super.onPostExecute(view);
            removeView(view);
            //if there are no more children left after top card removal let the callback know
            if (mAdapter.getCount() == 0) {
                eventCallback.cardsDepleted();
            }
        }
    }
}