package com.example.percobaan2;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout sidebar;
    private LinearLayout urlListContainer;
    private Button btnAddUrl;
    private Button btnCloseSidebar;
    private TextView tvHiddenTrigger;

    private final int LOCATION_PERMISSION_REQUEST = 1001;
    private final int NOTIFICATION_POLICY_REQUEST = 1002;
    private final int DEVICE_ADMIN_REQUEST = 1003;

    private String mGeolocationOrigin;
    private GeolocationPermissions.Callback mGeolocationCallback;

    // Notification Management
    private NotificationManager notificationManager;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    // Simpan state untuk rotasi
    private String currentUrl;
    private boolean webViewInitialized = false;

    // URL Management
    private List<String> urlList;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "WebViewPrefs";
    private static final String URL_LIST_KEY = "url_list";

    // Sidebar state
    private boolean isSidebarVisible = false;
    private Handler hideHandler = new Handler();
    private Runnable hideRunnable;

    // Touch detection untuk menampilkan sidebar
    private float touchStartX = 0;
    private static final int EDGE_SWIPE_THRESHOLD = 50; // pixel dari kiri untuk trigger

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup kiosk mode
        setupKioskMode();

        // Initialize notification blocking
        initializeNotificationBlocking();

        // Initialize components
        initializeComponents();

        // Load saved URLs
        loadSavedUrls();

        // Setup WebView
        setupWebView();

        // Setup sidebar
        setupSidebar();

        // Setup touch listener untuk edge swipe
        setupTouchListener();

        // Handle rotation - restore state atau load initial URL
        handleInitialLoad(savedInstanceState);

        // Request notification access
        requestNotificationAccess();
    }
    private void initializeNotificationBlocking() {
        try {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            // Set Do Not Disturb mode jika memungkinkan
            enableDoNotDisturbMode();

            // Start notification blocker service
            startNotificationBlockerService();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enableDoNotDisturbMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
                    // Set Do Not Disturb to block all notifications
                    NotificationManager.Policy policy = new NotificationManager.Policy(
                            0, // interruption filter - block all
                            0, // priority categories - none
                            0  // priority call senders - none
                    );
                    notificationManager.setNotificationPolicy(policy);
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startNotificationBlockerService() {
        try {
            Intent serviceIntent = new Intent(this, NotificationBlockerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestNotificationAccess() {
        try {
            // Request Notification Policy Access
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivityForResult(intent, NOTIFICATION_POLICY_REQUEST);
                }
            }

            // Request Notification Listener Access
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (!isNotificationServiceEnabled()) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivityForResult(intent, NOTIFICATION_POLICY_REQUEST);
                }
            }

            // Request Device Admin (optional, untuk kontrol lebih ketat)
            requestDeviceAdmin();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestDeviceAdmin() {
        try {
            if (devicePolicyManager != null && !devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable device admin to fully control kiosk mode and block notifications");
                startActivityForResult(intent, DEVICE_ADMIN_REQUEST);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!android.text.TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (pkgName.equals(cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    private void initializeComponents() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        sidebar = findViewById(R.id.sidebar);
        urlListContainer = findViewById(R.id.urlListContainer);
        btnAddUrl = findViewById(R.id.btnAddUrl);
        btnCloseSidebar = findViewById(R.id.btnCloseSidebar);
        tvHiddenTrigger = findViewById(R.id.tvHiddenTrigger);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        urlList = new ArrayList<>();
    }

    private void setupWebView() {
        try {
            // Konfigurasi WebView
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setGeolocationEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setDatabaseEnabled(true);

            // PENGATURAN WEBVIEW TANPA ZOOM DAN NON-RESPONSIVE
            webView.getSettings().setSupportZoom(false);
            webView.getSettings().setBuiltInZoomControls(false);
            webView.getSettings().setUseWideViewPort(false);
            webView.getSettings().setLoadWithOverviewMode(false);
            webView.setInitialScale(100);

            // Set lokasi database untuk API lama
            String databasePath = getApplicationContext().getDir("databases", MODE_PRIVATE).getPath();
            webView.getSettings().setGeolocationDatabasePath(databasePath);

            // WebViewClient
            webView.setWebViewClient(new WebViewClient(){
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    currentUrl = url;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    currentUrl = url;
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });

            // WebChromeClient
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onGeolocationPermissionsShowPrompt(String origin,
                                                               GeolocationPermissions.Callback callback) {
                    mGeolocationOrigin = origin;
                    mGeolocationCallback = callback;
                    checkLocationPermission();
                }

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    if (progressBar != null) {
                        if (newProgress < 100) {
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setProgress(newProgress);
                        } else {
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }
            });

            webViewInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing WebView: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleInitialLoad(Bundle savedInstanceState) {
        try {
            if (savedInstanceState != null && webViewInitialized) {
                // Restore dari saved instance state
                currentUrl = savedInstanceState.getString("current_url", getDefaultUrl());
                Bundle webViewState = savedInstanceState.getBundle("webview_state");
                if (webViewState != null && webView != null) {
                    webView.restoreState(webViewState);
                } else if (currentUrl != null && webView != null) {
                    webView.loadUrl(currentUrl);
                }
            } else {
                // Load initial URL
                currentUrl = getDefaultUrl();
                if (webView != null && webViewInitialized) {
                    webView.loadUrl(currentUrl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback ke default URL jika ada error
            currentUrl = getDefaultUrl();
            if (webView != null && webViewInitialized) {
                webView.loadUrl(currentUrl);
            }
        }
    }

    private void setupSidebar() {
        // Setup button add URL
        btnAddUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddUrlDialog();
            }
        });

        // Setup button close sidebar
        btnCloseSidebar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSidebar();
            }
        });

        // Setup hidden trigger (area kecil di kiri untuk memunculkan sidebar)
        tvHiddenTrigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSidebar();
            }
        });

        // Initially hide sidebar
        sidebar.setVisibility(View.GONE);

        // Populate URL list
        populateUrlList();
    }

    private void setupTouchListener() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                touchStartX = event.getX();
                                break;
                            case MotionEvent.ACTION_UP:
                                // Jika touch dimulai dari edge kiri, tampilkan sidebar
                                if (touchStartX <= EDGE_SWIPE_THRESHOLD && !isSidebarVisible) {
                                    showSidebar();
                                    return true;
                                }
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });
        }
    }

    private void loadSavedUrls() {
        try {
            String urlListJson = sharedPreferences.getString(URL_LIST_KEY, "[]");
            JSONArray jsonArray = new JSONArray(urlListJson);
            urlList.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                urlList.add(jsonArray.getString(i));
            }
            // Jika tidak ada URL tersimpan, tambahkan default
            if (urlList.isEmpty()) {
                urlList.add("http://10.2.8.23:3000/w.html");
                saveUrls();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            // Jika error, gunakan default
            urlList.clear();
            urlList.add("http://10.2.8.23:3000/w.html");
            saveUrls();
        }
    }

    private void saveUrls() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (String url : urlList) {
                jsonArray.put(url);
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(URL_LIST_KEY, jsonArray.toString());
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDefaultUrl() {
        return urlList.isEmpty() ? "http://10.2.8.23:3000/w.html" : urlList.get(0);
    }

    private void populateUrlList() {
        try {
            if (urlListContainer == null) return;

            urlListContainer.removeAllViews();

            for (int i = 0; i < urlList.size(); i++) {
                final String url = urlList.get(i);
                final int index = i;

                // Create container for each URL item
                LinearLayout urlItemContainer = new LinearLayout(this);
                urlItemContainer.setOrientation(LinearLayout.VERTICAL);
                urlItemContainer.setPadding(16, 8, 16, 8);

                // URL Button
                Button urlButton = new Button(this);
                urlButton.setText(url.length() > 30 ? url.substring(0, 30) + "..." : url);
                urlButton.setBackgroundColor(Color.parseColor("#33B5E5"));
                urlButton.setTextColor(Color.WHITE);
                urlButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            currentUrl = url;
                            if (webView != null && webViewInitialized) {
                                webView.loadUrl(url);
                            }
                            hideSidebar();
                            Toast.makeText(MainActivity.this, "Loading: " + url, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "Error loading URL", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                // Action buttons container
                LinearLayout actionContainer = new LinearLayout(this);
                actionContainer.setOrientation(LinearLayout.HORIZONTAL);

                // Edit button
                Button editButton = new Button(this);
                editButton.setText("Edit");
                editButton.setBackgroundColor(Color.parseColor("#FFBB33"));
                editButton.setTextColor(Color.WHITE);
                editButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                editButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showEditUrlDialog(index, url);
                    }
                });

                // Delete button
                Button deleteButton = new Button(this);
                deleteButton.setText("Delete");
                deleteButton.setBackgroundColor(Color.parseColor("#FF4444"));
                deleteButton.setTextColor(Color.WHITE);
                deleteButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDeleteConfirmDialog(index, url);
                    }
                });

                actionContainer.addView(editButton);
                actionContainer.addView(deleteButton);

                urlItemContainer.addView(urlButton);
                urlItemContainer.addView(actionContainer);

                // Add separator
                View separator = new View(this);
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 2));
                separator.setBackgroundColor(Color.GRAY);

                urlListContainer.addView(urlItemContainer);
                urlListContainer.addView(separator);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAddUrlDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Add New URL");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setHint("Enter URL (e.g., http://example.com)");

            // Perbaikan untuk setView
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(50, 40, 50, 10);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        String newUrl = input.getText().toString().trim();
                        if (!newUrl.isEmpty()) {
                            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                                newUrl = "http://" + newUrl;
                            }
                            urlList.add(newUrl);
                            saveUrls();
                            populateUrlList();
                            Toast.makeText(MainActivity.this, "URL added successfully", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error adding URL", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEditUrlDialog(int index, String currentUrl) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Edit URL");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setText(currentUrl);

            // Perbaikan untuk setView
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(50, 40, 50, 10);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        String updatedUrl = input.getText().toString().trim();
                        if (!updatedUrl.isEmpty()) {
                            if (!updatedUrl.startsWith("http://") && !updatedUrl.startsWith("https://")) {
                                updatedUrl = "http://" + updatedUrl;
                            }
                            urlList.set(index, updatedUrl);
                            saveUrls();
                            populateUrlList();
                            Toast.makeText(MainActivity.this, "URL updated successfully", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error updating URL", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showDeleteConfirmDialog(int index, String url) {
        try {
            if (urlList.size() <= 1) {
                Toast.makeText(this, "Cannot delete the last URL", Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Delete URL");
            builder.setMessage("Are you sure you want to delete this URL?\n\n" + url);

            builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        urlList.remove(index);
                        saveUrls();
                        populateUrlList();
                        Toast.makeText(MainActivity.this, "URL deleted successfully", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error deleting URL", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleSidebar() {
        if (isSidebarVisible) {
            hideSidebar();
        } else {
            showSidebar();
        }
    }

    private void showSidebar() {
        try {
            if (isSidebarVisible || sidebar == null) return;

            sidebar.setVisibility(View.VISIBLE);

            TranslateAnimation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f
            );
            slideIn.setDuration(300);
            sidebar.startAnimation(slideIn);

            isSidebarVisible = true;

            // Auto hide setelah 10 detik
            cancelAutoHide();
            hideRunnable = new Runnable() {
                @Override
                public void run() {
                    hideSidebar();
                }
            };
            hideHandler.postDelayed(hideRunnable, 10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideSidebar() {
        try {
            if (!isSidebarVisible || sidebar == null) return;

            TranslateAnimation slideOut = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f
            );
            slideOut.setDuration(300);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (sidebar != null) {
                        sidebar.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });

            sidebar.startAnimation(slideOut);
            isSidebarVisible = false;
            cancelAutoHide();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancelAutoHide() {
        try {
            if (hideRunnable != null && hideHandler != null) {
                hideHandler.removeCallbacks(hideRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        try {
            super.onSaveInstanceState(outState);

            // Save current URL
            if (currentUrl != null) {
                outState.putString("current_url", currentUrl);
            }

            // Save WebView state
            if (webView != null && webViewInitialized) {
                Bundle webViewState = new Bundle();
                webView.saveState(webViewState);
                outState.putBundle("webview_state", webViewState);
            }

            // Save sidebar state
            outState.putBoolean("sidebar_visible", isSidebarVisible);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);

            // Restore current URL
            currentUrl = savedInstanceState.getString("current_url", getDefaultUrl());

            // Restore sidebar state
            boolean wasSidebarVisible = savedInstanceState.getBoolean("sidebar_visible", false);
            if (wasSidebarVisible) {
                // Jangan langsung show sidebar, biarkan user mengaktifkannya lagi
                isSidebarVisible = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        try {
            super.onConfigurationChanged(newConfig);
            setupKioskMode();

            // Refresh UI components after configuration change
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (sidebar != null && !isSidebarVisible) {
                            sidebar.setVisibility(View.GONE);
                        }
                        setupKioskMode();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 100);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupKioskMode() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            setupKioskMode();
            if (webView != null) {
                webView.onResume();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            if (webView != null) {
                webView.onPause();
            }

            // Kiosk mode behavior
            ActivityManager activityManager = (ActivityManager) getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.moveTaskToFront(getTaskId(), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            cancelAutoHide();

            if (webView != null) {
                webView.clearHistory();
                webView.clearCache(true);
                webView.destroy();
                webView = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        try {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus) {
                setupKioskMode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkLocationPermission() {
        try {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST);
            } else {
                if (mGeolocationCallback != null) {
                    mGeolocationCallback.invoke(mGeolocationOrigin, true, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            if (requestCode == LOCATION_PERMISSION_REQUEST) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mGeolocationCallback != null) {
                        mGeolocationCallback.invoke(mGeolocationOrigin, true, false);
                    }
                    Toast.makeText(this, "Izin lokasi diberikan", Toast.LENGTH_SHORT).show();
                } else {
                    if (mGeolocationCallback != null) {
                        mGeolocationCallback.invoke(mGeolocationOrigin, false, false);
                    }
                    Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (isSidebarVisible) {
                hideSidebar();
            }
            // Disable back button untuk kiosk mode
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}