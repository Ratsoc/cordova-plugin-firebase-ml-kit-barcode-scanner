package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.content.Context;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.UiThread;

// ----------------------------------------------------------------------------
// |  ML Kit Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------

public class BarcodeScanningProcessor {
  private static final String TAG = "Barcode-Processor";

  // ML Kit barcode scanner
  private final BarcodeScanner _Detector;
  private final BarcodeUpdateListener _BarcodeUpdateListener;

  // Latest frame
  @GuardedBy("this")
  private ByteBuffer _LatestImage;
  @GuardedBy("this")
  private int _LatestWidth;
  @GuardedBy("this")
  private int _LatestHeight;
  @GuardedBy("this")
  private int _LatestRotation;
  @GuardedBy("this")
  private int _LatestFormat;

  // Frame being processed
  @GuardedBy("this")
  private ByteBuffer _ProcessingImage;
  @GuardedBy("this")
  private int _ProcessingWidth;
  @GuardedBy("this")
  private int _ProcessingHeight;
  @GuardedBy("this")
  private int _ProcessingRotation;
  @GuardedBy("this")
  private int _ProcessingFormat;

  public BarcodeScanningProcessor(Context context) {
    // Configure ML Kit to scan all formats
    BarcodeScannerOptions options =
      new BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build();
    _Detector = BarcodeScanning.getClient(options);

    if (context instanceof BarcodeUpdateListener) {
      _BarcodeUpdateListener = (BarcodeUpdateListener) context;
    } else {
      throw new RuntimeException(
        "Hosting activity must implement BarcodeUpdateListener"
      );
    }
  }

  /**
   * Queue a new frame for detection.
   * @param data ByteBuffer from the camera (e.g. NV21)
   * @param width image width in pixels
   * @param height image height in pixels
   * @param rotation rotationDegrees (0, 90, 180, 270)
   * @param imageFormat one of InputImage.IMAGE_FORMAT_* constants
   */
  public synchronized void process(
      ByteBuffer data,
      int width,
      int height,
      int rotation,
      int imageFormat
  ) {
    _LatestImage = data;
    _LatestWidth = width;
    _LatestHeight = height;
    _LatestRotation = rotation;
    _LatestFormat = imageFormat;

    // If no frame is being processed, start the pipeline
    if (_ProcessingImage == null) {
      processLatestImage();
    }
  }

  public void stop() {
    try {
      _Detector.close();
    } catch (IOException e) {
      Log.e(TAG, "Error closing BarcodeScanner", e);
    }
  }

  private synchronized void processLatestImage() {
    // Swap buffers
    _ProcessingImage    = _LatestImage;
    _ProcessingWidth    = _LatestWidth;
    _ProcessingHeight   = _LatestHeight;
    _ProcessingRotation = _LatestRotation;
    _ProcessingFormat   = _LatestFormat;

    _LatestImage = null;

    if (_ProcessingImage != null) {
      processImage(
        _ProcessingImage,
        _ProcessingWidth,
        _ProcessingHeight,
        _ProcessingRotation,
        _ProcessingFormat
      );
    }
  }

  private void processImage(
      ByteBuffer data,
      int width,
      int height,
      int rotation,
      int format
  ) {
    // Wrap the ByteBuffer in an ML Kit InputImage
    InputImage image = InputImage.fromByteBuffer(
      data,
      width,
      height,
      rotation,
      format
    );

    // Run detection
    _Detector
      .process(image)
      .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
        @Override
        public void onSuccess(List<Barcode> barcodes) {
          for (Barcode barcode : barcodes) {
            _BarcodeUpdateListener.onBarcodeDetected(barcode.getRawValue());
          }
          processLatestImage();
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(Exception e) {
          Log.e(TAG, "Barcode detection failed", e);
        }
      });
  }

  /**
   * Host activities implement this to receive callbacks.
   */
  public interface BarcodeUpdateListener {
    @UiThread
    void onBarcodeDetected(String barcodeValue);
  }
}
