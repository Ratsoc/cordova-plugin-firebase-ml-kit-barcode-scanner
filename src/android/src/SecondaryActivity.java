package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.common.api.CommonStatusCodes;


public class SecondaryActivity extends Activity implements View.OnClickListener {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------
  public static final String BarcodeValue = "FirebaseVisionBarcode";

  // ----------------------------------------------------------------------------
  // | Protected Properties
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final int    RC_BARCODE_CAPTURE = 9001         ;
  private static final String TAG                = "BarcodeMain";
  
  // private CompoundButton autoFocus    ;
  // private CompoundButton useFlash     ;
  // private TextView       statusMessage;
  // private TextView       barcodeValue ;

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ---------------------------------------------------------------------------- 
  @Override
  public void onClick(View p_View) {
    if (p_View.getId() == getResources().getIdentifier("read_barcode", "id", getPackageName())) {
      // launch barcode activity.
      Intent intent = new Intent(this, BarcodeCaptureActivity.class);

      startActivityForResult(intent, RC_BARCODE_CAPTURE);
    }
  }

  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------
  @Override
  protected void onCreate(Bundle p_SavedInstanceState) {
    super.onCreate(p_SavedInstanceState);

    setRequestedOrientation(getIntent().getIntExtra("Orientation", 1));
    setContentView(getResources().getIdentifier("activity_barcode_scanner", "layout", getPackageName()));

    findViewById(getResources().getIdentifier("read_barcode", "id", getPackageName())).setOnClickListener(this);

    Intent intent = new Intent(this, BarcodeCaptureActivity.class);

    intent.putExtra("DetectionTypes", getIntent().getIntExtra("DetectionTypes", 1234));
    intent.putExtra("ViewFinderWidth", getIntent().getDoubleExtra("DetectionTypes", .5));
    intent.putExtra("ViewFinderHeight", getIntent().getDoubleExtra("DetectionTypes", .7));
    intent.putExtra("Orientation", getIntent().getIntExtra("Orientation", 1));
    intent.putExtra("IgnoreCodes", getIntent().getStringArrayExtra("IgnoreCodes"));

    startActivityForResult(intent, RC_BARCODE_CAPTURE);
  }

  @Override
  protected void onActivityResult(int p_RequestCode, int p_ResultCode, Intent p_Data) {
    Log.d(TAG, "Activity exited");
    if (p_RequestCode == RC_BARCODE_CAPTURE) {
      Intent d = new Intent();
      if (p_ResultCode == CommonStatusCodes.SUCCESS) {
        if (p_Data != null) {
          String barcode = p_Data.getStringExtra(BarcodeCaptureActivity.BarcodeValue);
          d.putExtra(BarcodeValue, barcode);
          setResult(CommonStatusCodes.SUCCESS, p_Data);
        } else {
          d.putExtra("err", "USER_CANCELLED");
          setResult(CommonStatusCodes.ERROR, d);
        }
      } else {
        d.putExtra("err", "There was an error with the barcode reader.");
        setResult(CommonStatusCodes.ERROR, d);
      }
      finish();
    } else {
      super.onActivityResult(p_RequestCode, p_ResultCode, p_Data);
    }
  }

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
}
