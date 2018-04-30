package com.daliborjelnek.touchlessBrowser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created by Dalibor Jelínek on 21.04.2018.
 */

public class HandFreeWebView extends WebView {

    private SpeechRecognizer mSpeechRecognizerForSearch;
    private SpeechRecognizer mSpeechRecognizerForInput;
    private Intent mSpeechRecognizerIntent;

    private Context context;
    InputConnection ic;
    InputMethodManager im;


    public HandFreeWebView(final Context context, AttributeSet attrs) {
        super(context, attrs);

        im = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        this.context = context;
        mSpeechRecognizerForInput = SpeechRecognizer.createSpeechRecognizer(context);
        mSpeechRecognizerForSearch = SpeechRecognizer.createSpeechRecognizer(context);
        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                context.getPackageName());

        mSpeechRecognizerForInput.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onBeginningOfSpeech() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onBufferReceived(byte[] arg0) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onEndOfSpeech() {
                // TODO Auto-generated method stub
                ((MainActivity)context).setListening(false);
                im.hideSoftInputFromWindow(getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

            }

            @Override
            public void onError(int arg0) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onEvent(int arg0, Bundle arg1) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onReadyForSpeech(Bundle params) {
                // TODO Auto-generated method stub
                Toast.makeText(context, "Voice recording starts", Toast.LENGTH_SHORT).show();
                ((MainActivity)context).setListening(true);



            }

            @Override
            public void onResults(Bundle results) {
                // TODO Auto-generated method stub
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                im.hideSoftInputFromWindow(getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

                if(ic != null){
                    ic.commitText(matches.get(0),1);
                }


            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // TODO Auto-generated method stub

            }

        });

        mSpeechRecognizerForSearch.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                ((MainActivity)context).setListening(true);
                Toast.makeText(context, "I'm listening...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {
                ((MainActivity)context).setListening(false);
            }

            @Override
            public void onError(int i) {
                Toast.makeText(context, "I'm sorry I don't understand", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle bundle) {

                ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Toast.makeText(context, "searching " + matches.get(0), Toast.LENGTH_SHORT).show();
                try {
                    loadUrl("https://www.google.cz/search?q="+ URLEncoder.encode(matches.get(0), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });



    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }


    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
       ic = super.onCreateInputConnection(outAttrs);


       if(ic != null){
           InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
           imm.hideSoftInputFromWindow(this.getWindowToken(), 0);

           mSpeechRecognizerForInput.startListening(mSpeechRecognizerIntent);


           return null;

       }
       else return null;



    }

    public void simulateClick(float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp1 = new MotionEvent.PointerProperties();
        pp1.id = 0;
        pp1.toolType = MotionEvent.TOOL_TYPE_FINGER;
        properties[0] = pp1;
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc1 = new MotionEvent.PointerCoords();
        pc1.x = x;
        pc1.y = y;
        pc1.pressure = 1;
        pc1.size = 1;
        pointerCoords[0] = pc1;
        MotionEvent motionEvent = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, 1, properties,
                pointerCoords, 0,  0, 1, 1, 0, 0, 0, 0 );
        dispatchTouchEvent(motionEvent);

        motionEvent = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_UP, 1, properties,
                pointerCoords, 0,  0, 1, 1, 0, 0, 0, 0 );
        dispatchTouchEvent(motionEvent);



    }

    public void startListenVoice(){
        mSpeechRecognizerForSearch.startListening(mSpeechRecognizerIntent);

    }


}


