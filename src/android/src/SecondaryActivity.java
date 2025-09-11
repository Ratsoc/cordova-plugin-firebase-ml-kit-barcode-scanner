package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class SecondaryActivity extends Activity implements View.OnClickListener {
  public static final String BarcodeValue = "FirebaseVisionBarcode";

  private static final int RC_BARCODE_CAPTURE = 9001;
  private static final String TAG = "BarcodeMain";

  @Override
  public void onClick(View view) {
    int readBtnId = getResources().getIdentifier("read_barcode", "id", getPackageName());
    if (view.getId() == readBtnId) {
      Intent intent = new Intent(this, BarcodeCaptureActivity.class);
      startActivityForResult(intent, RC_BARCODE_CAPTURE);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    int layoutId = getResources().getIdentifier("activity_barcode_scanner", "layout", getPackageName());
    setContentView(layoutId);

    int readBtnId = getResources().getIdentifier("read_barcode", "id", getPackageName());
    findViewById(readBtnId).setOnClickListener(this);

    // Auto-launch scanner on activity start
    Intent intent = new Intent(this, BarcodeCaptureActivity.class);
    intent.putExtra("DetectionTypes", getIntent().getIntExtra("DetectionTypes", 1234));
    intent.putExtra("ViewFinderWidth",  getIntent().getDoubleExtra("ViewFinderWidth",  .5));
    intent.putExtra("ViewFinderHeight", getIntent().getDoubleExtra("ViewFinderHeight", .7));
    startActivityForResult(intent, RC_BARCODE_CAPTURE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "Activity exited");
    if (requestCode == RC_BARCODE_CAPTURE) {
      Intent resultIntent = new Intent();
      if (resultCode == Activity.RESULT_OK) {
        if (data != null) {
          String barcode = data.getStringExtra(BarcodeCaptureActivity.BarcodeValue);
          resultIntent.putExtra(BarcodeValue, barcode);
          setResult(Activity.RESULT_OK, resultIntent);
        } else {
          resultIntent.putExtra("err", "USER_CANCELLED");
          setResult(Activity.RESULT_CANCELED, resultIntent);
        }
      } else {
        resultIntent.putExtra("err", "There was an error with the barcode reader.");
        setResult(Activity.RESULT_CANCELED, resultIntent);
      }
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}