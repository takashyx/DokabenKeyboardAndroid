/*
 * Copyright (C) 2008-2009 The Android Open Source Project
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

package com.takashyx.softkeyboard;

import android.app.Dialog;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.IBinder;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;
    
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

    private InputMethodManager mInputMethodManager;

    private DokabenFlingKeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;
    
    private DokabenFlingKeyboard mQwertyKeyboard;
    private DokabenFlingKeyboard mCurKeyboard;
    
    private String mWordSeparators;

    ArrayList<String> mCandidateList = new ArrayList<String>();

    /* store last Keydown for swipe */
    private int mKeyDownKeyCode;

    /*swipe status*/
    private int mSwipeDirection;
    private boolean mPressing;

    private int [][] dokaben_keycode_array = {
            //center, left, up, right, down
            {0x30A2, 0x30A4, 0x30A6, 0x30A8, 0x30AA}, // あいうえお
            {0x30AB, 0x30AD, 0x30AF, 0x30B1, 0x30B3}, // かきくけこ
            {0x30B5, 0x30B7, 0x30B9, 0x30BB, 0x30BD}, // さしすせそ
            {0x30BF, 0x30C1, 0x30C4, 0x30C6, 0x30C8}, // たちつてと
            {0x30CA, 0x30CB, 0x30CC, 0x30CD, 0x30CE}, // なにぬねの
            {0x30CF, 0x30D2, 0x30D5, 0x30D8, 0x30DB}, // はひふへほ
            {0x30DE, 0x30DF, 0x30E0, 0x30E1, 0x30E2}, // まみむめも
            {0x30E4, 0,      0x30E6, 0,      0x30E8}, // や（ゆ）よ
            {0x30E9, 0x30EA, 0x30EB, 0x30EC, 0x30ED}, // らりるれろ
            //（濁点半濁点大小）゛（小）゜（なし）    -> handleDakuten()で処理
            {0x30EF, 0x30F2, 0x30F3, 0x30FC, 0}, // わをんー
            //？！ー -> handleKigou()で処理
    };

    private Map<Integer, Integer> dokaben_keycode_array_index;
    private Map<String, String> dakuten_convert_array;
    private Map<String, String> kigou_convert_array;
    private Map<String, String> dokaben_convert_array;


    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);

        dokaben_keycode_array_index = new HashMap<Integer, Integer>();
        dokaben_keycode_array_index.put(getIntFromUTF8("ア"),0);
        dokaben_keycode_array_index.put(getIntFromUTF8("カ"),1);
        dokaben_keycode_array_index.put(getIntFromUTF8("サ"),2);
        dokaben_keycode_array_index.put(getIntFromUTF8("タ"),3);
        dokaben_keycode_array_index.put(getIntFromUTF8("ナ"),4);
        dokaben_keycode_array_index.put(getIntFromUTF8("ハ"),5);
        dokaben_keycode_array_index.put(getIntFromUTF8("マ"),6);
        dokaben_keycode_array_index.put(getIntFromUTF8("ヤ"),7);
        dokaben_keycode_array_index.put(getIntFromUTF8("ラ"),8);
        dokaben_keycode_array_index.put(getIntFromUTF8("ワ"),9);
        dokaben_keycode_array_index.put(getIntFromUTF8("、"),10);

        dakuten_convert_array = new HashMap<String, String>();
        dakuten_convert_array.put("カ","ガ");
        dakuten_convert_array.put("ガ","カ");
        dakuten_convert_array.put("キ","ギ");
        dakuten_convert_array.put("ギ","キ");
        dakuten_convert_array.put("ク","グ");
        dakuten_convert_array.put("グ","ク");
        dakuten_convert_array.put("ケ","ゲ");
        dakuten_convert_array.put("ゲ","ケ");
        dakuten_convert_array.put("コ","ゴ");
        dakuten_convert_array.put("ゴ","コ");

        dakuten_convert_array.put("サ","ザ");
        dakuten_convert_array.put("ザ","サ");
        dakuten_convert_array.put("シ","ジ");
        dakuten_convert_array.put("ジ","シ");
        dakuten_convert_array.put("ス","ズ");
        dakuten_convert_array.put("ズ","ス");
        dakuten_convert_array.put("セ","ゼ");
        dakuten_convert_array.put("ゼ","セ");
        dakuten_convert_array.put("ソ","ゾ");
        dakuten_convert_array.put("ゾ","ソ");

        dakuten_convert_array.put("タ","ダ");
        dakuten_convert_array.put("ダ","タ");
        dakuten_convert_array.put("チ","ヂ");
        dakuten_convert_array.put("ヂ","チ");
        dakuten_convert_array.put("ツ","ッ");
        dakuten_convert_array.put("ッ","ヅ");
        dakuten_convert_array.put("ヅ","ツ");
        dakuten_convert_array.put("テ","デ");
        dakuten_convert_array.put("デ","テ");
        dakuten_convert_array.put("ト","ド");
        dakuten_convert_array.put("ド","ト");

        dakuten_convert_array.put("ハ","バ");
        dakuten_convert_array.put("バ","パ");
        dakuten_convert_array.put("パ","ハ");
        dakuten_convert_array.put("ヒ","ビ");
        dakuten_convert_array.put("ビ","ピ");
        dakuten_convert_array.put("ピ","ヒ");
        dakuten_convert_array.put("フ","ブ");
        dakuten_convert_array.put("ブ","プ");
        dakuten_convert_array.put("プ","フ");
        dakuten_convert_array.put("ヘ","ベ");
        dakuten_convert_array.put("ベ","ペ");
        dakuten_convert_array.put("ペ","ヘ");
        dakuten_convert_array.put("ホ","ボ");
        dakuten_convert_array.put("ボ","ポ");
        dakuten_convert_array.put("ポ","ホ");

        dakuten_convert_array.put("ア","ァ");
        dakuten_convert_array.put("ァ","ア");
        dakuten_convert_array.put("イ","ィ");
        dakuten_convert_array.put("ィ","イ");
        dakuten_convert_array.put("ウ","ゥ");
        dakuten_convert_array.put("ゥ","ウ");
        dakuten_convert_array.put("エ","ェ");
        dakuten_convert_array.put("ェ","エ");
        dakuten_convert_array.put("オ","ォ");
        dakuten_convert_array.put("ォ","オ");
        dakuten_convert_array.put("ヤ","ャ");
        dakuten_convert_array.put("ャ","ヤ");
        dakuten_convert_array.put("ユ","ュ");
        dakuten_convert_array.put("ュ","ユ");
        dakuten_convert_array.put("ヨ","ョ");
        dakuten_convert_array.put("ョ","ヨ");


        dokaben_convert_array = new HashMap<String, String>();
        dokaben_convert_array.put("ア",":dokaben_a:");
        dokaben_convert_array.put("イ",":dokaben_i:");
        dokaben_convert_array.put("ウ",":dokaben_u:");
        dokaben_convert_array.put("エ",":dokaben_e:");
        dokaben_convert_array.put("オ",":dokaben_o:");
        dokaben_convert_array.put("ァ",":dokaben_a_small:");
        dokaben_convert_array.put("ィ",":dokaben_i_small:");
        dokaben_convert_array.put("ゥ",":dokaben_u_small:");
        dokaben_convert_array.put("ェ",":dokaben_e_small:");
        dokaben_convert_array.put("ォ",":dokaben_o_small:");
        dokaben_convert_array.put("カ",":dokaben_ka:");
        dokaben_convert_array.put("キ",":dokaben_ki:");
        dokaben_convert_array.put("ク",":dokaben_ku:");
        dokaben_convert_array.put("ケ",":dokaben_ke:");
        dokaben_convert_array.put("コ",":dokaben_ko:");
        dokaben_convert_array.put("ガ",":dokaben_ga:");
        dokaben_convert_array.put("ギ",":dokaben_gi:");
        dokaben_convert_array.put("グ",":dokaben_gu:");
        dokaben_convert_array.put("ゲ",":dokaben_ge:");
        dokaben_convert_array.put("ゴ",":dokaben_go:");
        dokaben_convert_array.put("サ",":dokaben_sa:");
        dokaben_convert_array.put("シ",":dokaben_si:");
        dokaben_convert_array.put("ス",":dokaben_su:");
        dokaben_convert_array.put("セ",":dokaben_se:");
        dokaben_convert_array.put("ソ",":dokaben_so:");
        dokaben_convert_array.put("ザ",":dokaben_za:");
        dokaben_convert_array.put("ジ",":dokaben_zi:");
        dokaben_convert_array.put("ズ",":dokaben_zu:");
        dokaben_convert_array.put("ゼ",":dokaben_ze:");
        dokaben_convert_array.put("ゾ",":dokaben_zo:");
        dokaben_convert_array.put("タ",":dokaben_ta:");
        dokaben_convert_array.put("チ",":dokaben_ti:");
        dokaben_convert_array.put("ツ",":dokaben_tu:");
        dokaben_convert_array.put("テ",":dokaben_te:");
        dokaben_convert_array.put("ト",":dokaben_to:");
        dokaben_convert_array.put("ダ",":dokaben_da:");
        dokaben_convert_array.put("ヂ",":dokaben_di:");
        dokaben_convert_array.put("ヅ",":dokaben_du:");
        dokaben_convert_array.put("デ",":dokaben_de:");
        dokaben_convert_array.put("ド",":dokaben_do:");
        dokaben_convert_array.put("ナ",":dokaben_na:");
        dokaben_convert_array.put("ニ",":dokaben_ni:");
        dokaben_convert_array.put("ヌ",":dokaben_nu:");
        dokaben_convert_array.put("ネ",":dokaben_ne:");
        dokaben_convert_array.put("ノ",":dokaben_no:");
        dokaben_convert_array.put("ハ",":dokaben_ha:");
        dokaben_convert_array.put("ヒ",":dokaben_hi:");
        dokaben_convert_array.put("フ",":dokaben_hu:");
        dokaben_convert_array.put("ヘ",":dokaben_he:");
        dokaben_convert_array.put("ホ",":dokaben_ho:");
        dokaben_convert_array.put("バ",":dokaben_ba:");
        dokaben_convert_array.put("ビ",":dokaben_bi:");
        dokaben_convert_array.put("ブ",":dokaben_bu:");
        dokaben_convert_array.put("ベ",":dokaben_be:");
        dokaben_convert_array.put("ボ",":dokaben_bo:");
        dokaben_convert_array.put("パ",":dokaben_pa:");
        dokaben_convert_array.put("ピ",":dokaben_pi:");
        dokaben_convert_array.put("プ",":dokaben_pu:");
        dokaben_convert_array.put("ペ",":dokaben_pe:");
        dokaben_convert_array.put("ポ",":dokaben_po:");
        dokaben_convert_array.put("マ",":dokaben_ma:");
        dokaben_convert_array.put("ミ",":dokaben_mi:");
        dokaben_convert_array.put("ム",":dokaben_mu:");
        dokaben_convert_array.put("メ",":dokaben_me:");
        dokaben_convert_array.put("モ",":dokaben_mo:");
        dokaben_convert_array.put("ヤ",":dokaben_ya:");
        dokaben_convert_array.put("ユ",":dokaben_yu:");
        dokaben_convert_array.put("ヨ",":dokaben_yo:");
        dokaben_convert_array.put("ラ",":dokaben_ra:");
        dokaben_convert_array.put("リ",":dokaben_ri:");
        dokaben_convert_array.put("ル",":dokaben_ru:");
        dokaben_convert_array.put("レ",":dokaben_re:");
        dokaben_convert_array.put("ロ",":dokaben_ro:");
        dokaben_convert_array.put("ッ",":dokaben_tu_small:");
        dokaben_convert_array.put("ャ",":dokaben_ya_small:");
        dokaben_convert_array.put("ュ",":dokaben_yu_small:");
        dokaben_convert_array.put("ョ",":dokaben_yo_small:");
        dokaben_convert_array.put("ワ",":dokaben_wa:");
        dokaben_convert_array.put("ヲ",":dokaben_wo:");
        dokaben_convert_array.put("ン",":dokaben_n:");
        dokaben_convert_array.put("ー",":dokaben_-:");
        dokaben_convert_array.put("！",":dokaben_bikkuri:");
        dokaben_convert_array.put("？",":dokaben_hatena:");

        kigou_convert_array = new HashMap<String, String>();
        kigou_convert_array.put("？","！");
        kigou_convert_array.put("！","？");
        kigou_convert_array.put("！","ー");
    }
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        //super.onInitializeInterface();
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new DokabenFlingKeyboard(this, R.xml.key_layout);
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (DokabenFlingKeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setOnTouchListener(new OnSwipeTouchListener(this)
        {
            public void onKeySwipeLeft() {
//                Log.i("dokaben", "onKeySwipeLeft");
                mSwipeDirection = 1;
            }

            public void onKeySwipeUp() {
//                Log.i("dokaben", "onKeySwipeUp");
                mSwipeDirection = 2;
            }

            public void onKeySwipeRight(){
//                Log.i("dokaben", "onKeySwipeRight");
                mSwipeDirection = 3;
            }

            public void onKeySwipeDown() {
//                Log.i("dokaben", "onKeySwipeDown ");
                mSwipeDirection = 4;
            }
        });

        setDokabenKeyboard(mQwertyKeyboard);
        mInputView.setPreviewEnabled(false);
        return mInputView;
    }

    private void setDokabenKeyboard(DokabenFlingKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey =
                mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);
        mInputView.setKeyboard(nextKeyboard);
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }
        
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;
                
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }
                
                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }
                
                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }
                
                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        setDokabenKeyboard(mCurKeyboard);
        mInputView.closing();
        final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < completions.length; i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }
        
        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }
        
        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }
        
        onKey(c, null);
        
        return true;
    }
    
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;
                
            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;
                
            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
                /* handle swpipe */
                else {
                    mKeyDownKeyCode = keyCode;
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.

        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }
        
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null 
                && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }
    
    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
        String s = "[";
        for (int i:keyCodes){
            s = s + String.valueOf(i) + ", ";
        }
        s = s.substring(0,s.length()-2);
        s = s + "]";

//        Log.i("dokaben", "onKey primaryCode:" + String.valueOf(primaryCode) + " keyCodes: " + s);

//        if (mPressing) {
//            return;
//        }
//        else if (isWordSeparator(primaryCode)) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch();
            return;
        } else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_CURSOR_LEFT) {
            handleCursorLeft();
        } else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_CURSOR_RIGHT) {
            handleCursorRight();
        }else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_DAKUTEN) {
            handleDakuten();
        }else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_KIGOU) {
            handleKigou();
        }else if (primaryCode == DokabenFlingKeyboardView.KEYCODE_DOKABEN) {
            handleDokaben();
        }
        // handle swipe
        else if (0x30A2 <= primaryCode && primaryCode <= 0x30FF) {
//            Log.i("dokaben","catch PriaryCode: " + String.valueOf(primaryCode) );
        }
        else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                mCandidateList.clear();
                mCandidateList.add(mComposing.toString());
                mCandidateList.add(dokabenConvert(mComposing.toString()));
                setSuggestions(mCandidateList, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }
    
    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }
        
        checkToggleCapsLock();
        mInputView.setShifted(mCapsLock || !mInputView.isShifted());
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
//        if (isInputViewShown()) {
//            if (mInputView.isShifted()) {
//                primaryCode = Character.toUpperCase(primaryCode);
//            }
//        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            Log.i("dokaben", "path4");
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
        }
    }

    private void handleCursorLeft() {
        InputConnection ic = getCurrentInputConnection();
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        int selectionStart = et.selectionStart;
        int selectionEnd = et.selectionEnd;

        if (selectionStart == selectionEnd && selectionStart != 0) {
            ic.setSelection(selectionStart-1, selectionEnd-1);
            updateCandidates();
        }
    }

    private void handleCursorRight() {
        InputConnection ic = getCurrentInputConnection();
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        int selectionStart = et.selectionStart;
        int selectionEnd = et.selectionEnd;

        if (selectionStart == selectionEnd && selectionStart != et.text.length()) {
            ic.setSelection(selectionStart+1, selectionEnd+1);
            updateCandidates();
        }
    }

    private void handleDakuten() {
        //get previous charactor
        String m = mComposing.toString();
        String s = getCurrentInputConnection().getTextBeforeCursor(1,0).toString();
        // check
        if (dakuten_convert_array.containsKey(s) && m.length() > 0)
        {
            mComposing.replace(m.length()-1, m.length(), dakuten_convert_array.get(s));
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        }
    }

    private void handleKigou() {
        //get previous charactor
        String m = mComposing.toString();
        String s = getCurrentInputConnection().getTextBeforeCursor(1,0).toString();
        // check
        if (kigou_convert_array.containsKey(s) && m.length() > 0) {
            mComposing.replace(m.length() - 1, m.length(), kigou_convert_array.get(s));
        }
        else {
            mComposing.append("？");
        }
        getCurrentInputConnection().setComposingText(mComposing, 1);
        updateShiftKeyState(getCurrentInputEditorInfo());
        updateCandidates();
    }

    private void handleDokaben() {
        String s = dokabenConvert(mComposing.toString());
        InputConnection ic = getCurrentInputConnection();
//        Log.i("dokaben","handleDokaben: "+s);
        ic.commitText(s, s.length());

    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    private void handleLanguageSwitch() {
        mInputMethodManager.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */);
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators() {
        return mWordSeparators;
    }

    private int getIntFromUTF8(String s) {
        try {
            char [] c = s.toCharArray();
            return (int)c[0];

        }catch (Exception e){
            return -1;
        }
    }

    private String dokabenConvert(String s){
        String out = "";
        for (String c : s.split("")){
            if(dokaben_convert_array.containsKey(c)) {
                out = out + dokaben_convert_array.get(c);
            }
            else {
                out = out + c;
            }
        }
        return out;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }
    
    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
//            commitTyped(getCurrentInputConnection());
            String s = mCandidateList.get(index);
            getCurrentInputConnection().commitText(s, s.length());
        }
    }

    public void onPress(int primaryCode) {
        mKeyDownKeyCode = primaryCode;
        mSwipeDirection = 0;
        mPressing = true;
    }
    
    public void onRelease(int primaryCode) {
        // send char here
        if(0x30A2 <= mKeyDownKeyCode && mKeyDownKeyCode <= 0x30FF){
            // Log.i("dokaben", "mKeyDownKeyCode: "+ String.valueOf(mKeyDownKeyCode));
            int FinalInputUnicode = dokaben_keycode_array[dokaben_keycode_array_index.get(mKeyDownKeyCode)][mSwipeDirection];
            int[] keyCodes = {FinalInputUnicode};
            handleCharacter(FinalInputUnicode, keyCodes);
        }
        mSwipeDirection = 0;
        mPressing = false;
    }

    public void swipeRight() {
//        Log.i("dokaben", "swipeRight");
    }

    public void swipeLeft() {
//        Log.i("dokaben", "swipeLeft");
    }

    public void swipeUp() {
//        Log.i("dokaben", "swipeUp");
    }

    public void swipeDown() {
//        Log.i("dokaben", "swipeDown");
    }

    @Override
    public InputConnection getCurrentInputConnection() {
        return super.getCurrentInputConnection();
    }
}