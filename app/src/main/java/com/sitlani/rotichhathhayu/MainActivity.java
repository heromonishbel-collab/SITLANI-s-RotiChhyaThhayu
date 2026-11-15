package com.sitlani.rotichhathhayu;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sitlani's Roti Chha Thhayu - native shell around the web app in assets/.
 * Adds: real voice recognition (SpeechRecognizer), text-to-speech, camera/gallery for fridge scan,
 * share sheet and the Android back button.
 */
public class MainActivity extends AppCompatActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 11;
    private static final int REQ_MIC = 12;
    private static final int REQ_CAM = 13;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private File photoFile;
    private File videoFile;
    private Uri photoUri;
    private Uri videoUri;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingLang = "hi-IN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#24060F"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA}, REQ_CAM);
                } else {
                    launchChooser(true);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        tts = new TextToSpeech(this, status -> ttsReady = (status == TextToSpeech.SUCCESS));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript(
                        "(function(){try{return window.__back?window.__back():false}catch(e){return false}})()",
                        value -> {
                            if (!"true".equals(value)) finish();
                        });
            }
        });

        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    /* ---------------- JS bridge ---------------- */
    public class Bridge {
        @JavascriptInterface
        public boolean isNative() {
            return true;
        }

        @JavascriptInterface
        public void startListening(final String lang) {
            runOnUiThread(() -> startSpeech(lang));
        }

        @JavascriptInterface
        public void stopListening() {
            runOnUiThread(() -> {
                if (recognizer != null) recognizer.stopListening();
            });
        }

        @JavascriptInterface
        public void speak(final String text, final String lang) {
            runOnUiThread(() -> doSpeak(text, lang));
        }

        @JavascriptInterface
        public void stopSpeak() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
            });
        }

        @JavascriptInterface
        public void share(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, null));
            });
        }
    }

    private void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    /* ---------------- voice in ---------------- */
    private void startSpeech(String lang) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingLang = lang;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("window.__sr&&__sr('error','no-recognizer');window.__sr&&__sr('end','')");
            return;
        }
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String t = firstResult(partialResults);
                if (t != null) js("window.__sr&&__sr('partial'," + JSONObject.quote(t) + ")");
            }

            @Override
            public void onResults(Bundle results) {
                String t = firstResult(results);
                if (t != null) js("window.__sr&&__sr('final'," + JSONObject.quote(t) + ")");
                js("window.__sr&&__sr('end','')");
            }

            @Override
            public void onError(int error) {
                String code;
                if (error == 6 || error == 7) code = "no-speech";
                else if (error == 9) code = "not-allowed";
                else if (error == 12 || error == 13) code = "language-not-supported";
                else code = "error";
                js("window.__sr&&__sr('error','" + code + "')");
                js("window.__sr&&__sr('end','')");
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(i);
    }

    private String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (l != null && !l.isEmpty()) ? l.get(0) : null;
    }

    /* ---------------- voice out ---------------- */
    private void doSpeak(String text, String lang) {
        if (tts == null || !ttsReady) {
            js("window.__ttsDone&&__ttsDone()");
            return;
        }
        Locale loc = (lang != null && lang.startsWith("en")) ? new Locale("en", "IN") : new Locale("hi", "IN");
        tts.setLanguage(loc);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { js("window.__ttsDone&&__ttsDone()"); }
            @Override public void onError(String utteranceId) { js("window.__ttsDone&&__ttsDone()"); }
        });
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "amma");
    }

    /* ---------------- camera / gallery ---------------- */
    private void launchChooser(boolean withCamera) {
        List<Intent> extra = new ArrayList<>();
        if (withCamera) {
            try {
                File dir = new File(getCacheDir(), "images");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                photoFile = File.createTempFile("fridge_", ".jpg", dir);
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                extra.add(cam);

                videoFile = File.createTempFile("fridge_", ".mp4", dir);
                videoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", videoFile);
                Intent vid = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                vid.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                vid.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);
                vid.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                vid.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                extra.add(vid);
            } catch (Exception e) {
                photoFile = null;
                videoFile = null;
                extra.clear();
            }
        }
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        Intent chooser = Intent.createChooser(pick, "Fridge photo / video");
        if (!extra.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            finishChooser(null);
        }
    }

    private void finishChooser(Uri[] result) {
        if (fileCb != null) {
            fileCb.onReceiveValue(result);
            fileCb = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) return;
        Uri[] out = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                out = new Uri[n];
                for (int i = 0; i < n; i++) out[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                out = new Uri[]{data.getData()};
            } else if (photoFile != null && photoFile.length() > 0) {
                out = new Uri[]{photoUri};
            } else if (videoFile != null && videoFile.length() > 0) {
                out = new Uri[]{videoUri};
            }
        }
        finishChooser(out);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_MIC) {
            if (granted) {
                startSpeech(pendingLang);
            } else {
                js("window.__sr&&__sr('error','not-allowed');window.__sr&&__sr('end','')");
            }
        } else if (requestCode == REQ_CAM) {
            launchChooser(granted);
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
