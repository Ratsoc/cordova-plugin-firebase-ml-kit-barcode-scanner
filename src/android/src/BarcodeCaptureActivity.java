package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

// ----------------------------------------------------------------------------
// |  Google Play Services Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;

// ----------------------------------------------------------------------------
// |  ML Kit Imports
// ----------------------------------------------------------------------------
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.io.IOException;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------
import tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera.CameraSource2;
import tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera.CameraSourcePreview;
import tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera.GraphicOverlay;

public final class BarcodeCaptureActivity
    extends AppCompatActivity
    implements BarcodeScanningProcessor.BarcodeUpdateListener {

  public Integer DetectionTypes;
  public double  ViewFinderWidth  = .5;
  public double  ViewFinderHeight = .7;
  public static final String BarcodeValue = "FirebaseVisionBarcode";

  private static final String TAG                   = "Barcode-reader";
  private static final int    RC_HANDLE_GMS         = 9001;
  private static final int    RC_HANDLE_CAMERA_PERM = 2;

  private CameraSource2                   _CameraSource;
  private CameraSourcePreview             _Preview;
  private GraphicOverlay<BarcodeGraphic>  _GraphicOverlay;
  private ScaleGestureDetector            _ScaleGestureDetector;
  private GestureDetector                 _GestureDetector;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    // Hide status bar + action bar
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    getWindow().setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    if (getActionBar() != null) getActionBar().hide();
    if (getSupportActionBar() != null) getSupportActionBar().hide();

    setContentView(
      getResources().getIdentifier("barcode_capture", "layout", getPackageName()));

    _Preview = (CameraSourcePreview) findViewById(
      getResources().getIdentifier("preview", "id", getPackageName()));
    _Preview.ViewFinderWidth  = ViewFinderWidth;
    _Preview.ViewFinderHeight = ViewFinderHeight;
    _GraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(
      getResources().getIdentifier("graphicOverlay", "id", getPackageName()));

    DetectionTypes    = getIntent().getIntExtra("DetectionTypes", 1234);
    ViewFinderWidth   = getIntent().getDoubleExtra("ViewFinderWidth", .5);
    ViewFinderHeight  = getIntent().getDoubleExtra("ViewFinderHeight", .7);

    int rc = ActivityCompat.checkSelfPermission(
      this, Manifest.permission.CAMERA);
    if (rc == PackageManager.PERMISSION_GRANTED) {
      createCameraSource(true, false);
    } else {
      requestCameraPermission();
    }

    _GestureDetector      = new GestureDetector(this, new CaptureGestureListener());
    _ScaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    boolean b = _ScaleGestureDetector.onTouchEvent(e);
    boolean c = _GestureDetector.onTouchEvent(e);
    return b || c || super.onTouchEvent(e);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode,
      @NonNull String[] permissions,
      @NonNull int[] grantResults) {

    if (requestCode != RC_HANDLE_CAMERA_PERM) {
      Log.d(TAG, "Unexpected permission result: " + requestCode);
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      return;
    }

    if (grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      DetectionTypes    = getIntent().getIntExtra("DetectionTypes", 0);
      ViewFinderWidth   = getIntent().getDoubleExtra("ViewFinderWidth", .5);
      ViewFinderHeight  = getIntent().getDoubleExtra("ViewFinderHeight", .7);
      createCameraSource(true, false);
      return;
    }

    Log.e(TAG,
      "Camera permission not granted. results len="
      + grantResults.length);
    new AlertDialog.Builder(this)
      .setTitle("Camera permission required")
      .setMessage(
        getResources().getIdentifier(
          "no_camera_permission", "string", getPackageName()))
      .setPositiveButton(
        getResources().getIdentifier("ok","string",getPackageName()),
        (dialog, id) -> finish())
      .show();
  }

  @Override
  public void onBarcodeDetected(String barcode) {
    Intent data = new Intent();
    data.putExtra(BarcodeValue, barcode);
    setResult(Activity.RESULT_OK, data);
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    startCameraSource();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (_Preview != null) _Preview.stop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (_Preview != null) _Preview.release();
  }

  @SuppressLint("InlinedApi")
  private void createCameraSource(boolean autoFocus, boolean useFlash) {
    int detectionType = (DetectionTypes == 0 || DetectionTypes == 1234)
      ? (Barcode.FORMAT_CODE_39 | Barcode.FORMAT_DATA_MATRIX)
      : DetectionTypes;

    BarcodeScannerOptions options =
      new BarcodeScannerOptions.Builder()
        .setBarcodeFormats(detectionType)
        .build();
    BarcodeScanner barcodeScanner = BarcodeScanning.getClient(options);
    BarcodeScanningProcessor scanningProcessor =
      new BarcodeScanningProcessor(this);

    CameraSource2.Builder builder = new CameraSource2.Builder(
        getApplicationContext(), scanningProcessor)
      .setFacing(CameraSource2.CAMERA_FACING_BACK)
      .setRequestedPreviewSize(1600, 1024)
      .setRequestedFps(15.0f);

    if (Build.VERSION.SDK_INT
        >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      builder.setFocusMode(
        autoFocus
          ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
          : null);
    }

    _CameraSource = builder
      .setFlashMode(
        useFlash
          ? Camera.Parameters.FLASH_MODE_TORCH
          : null)
      .build();
  }

  private void startCameraSource() {
    int code = GoogleApiAvailability
      .getInstance()
      .isGooglePlayServicesAvailable(getApplicationContext());
    if (code != ConnectionResult.SUCCESS) {
      Dialog dlg = GoogleApiAvailability
        .getInstance()
        .getErrorDialog(this, code, RC_HANDLE_GMS);
      dlg.show();
    }

    if (_CameraSource != null) {
      try {
        _Preview.start(_CameraSource, _GraphicOverlay);
      } catch (IOException e) {
        Log.e(TAG, "Unable to start camera source.", e);
        _CameraSource.release();
        _CameraSource = null;
      }
    }
  }

  private void requestCameraPermission() {
    Log.w(TAG, "Requesting camera permission");
    final String[] permissions = { Manifest.permission.CAMERA };
    if (!ActivityCompat.shouldShowRequestPermissionRationale(
          this, Manifest.permission.CAMERA)) {
      ActivityCompat.requestPermissions(
        this, permissions, RC_HANDLE_CAMERA_PERM);
      return;
    }
    View.OnClickListener listener = view ->
      ActivityCompat.requestPermissions(
        BarcodeCaptureActivity.this,
        permissions,
        RC_HANDLE_CAMERA_PERM);

    findViewById(
      getResources().getIdentifier(
        "topLayout","id",getPackageName()))
      .setOnClickListener(listener);

    Snackbar.make(
        _GraphicOverlay,
        getResources().getIdentifier(
          "permission_camera_rationale","string",getPackageName()),
        Snackbar.LENGTH_INDEFINITE)
      .setAction(
        getResources().getIdentifier("ok","string",getPackageName()),
        v -> ActivityCompat.requestPermissions(
          BarcodeCaptureActivity.this,
          permissions,
          RC_HANDLE_CAMERA_PERM))
      .show();
  }

  private class CaptureGestureListener
      extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      return super.onSingleTapConfirmed(e);
    }
  }

  private class ScaleListener
      implements ScaleGestureDetector.OnScaleGestureListener {
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
      _CameraSource.doZoom(detector.getScaleFactor());
    }
  }
}