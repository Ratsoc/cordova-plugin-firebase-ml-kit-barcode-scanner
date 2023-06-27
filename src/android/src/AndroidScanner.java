package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

// ----------------------------------------------------------------------------
// |  Cordova Imports
// ----------------------------------------------------------------------------
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import org.json.JSONArray;
import org.json.JSONException;
import com.google.android.gms.common.api.CommonStatusCodes;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import javax.security.auth.callback.Callback;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------

public class AndroidScanner extends CordovaPlugin {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // | Protected Properties
  // ----------------------------------------------------------------------------
  protected CallbackContext CallbackContext;

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final int RC_BARCODE_CAPTURE = 9001;

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  @Override
  public boolean execute(String p_Action, JSONArray p_Args, CallbackContext p_CallbackContext) throws JSONException {
    Context context = cordova.getActivity().getApplicationContext();
    CallbackContext = p_CallbackContext;

    if (p_Action.equals("startScan")) {
      Thread thread = new Thread(new OneShotTask(context, p_Args));
      thread.start();
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int p_RequestCode, int p_ResultCode, Intent p_Data) {
    super.onActivityResult(p_RequestCode, p_ResultCode, p_Data);

    if (p_RequestCode == RC_BARCODE_CAPTURE) {
      if (p_ResultCode == CommonStatusCodes.SUCCESS) {
        Intent d = new Intent();
        if (p_Data != null) {
          String barcode = p_Data.getStringExtra(BarcodeCaptureActivity.BarcodeValue);
          JSONArray result = new JSONArray();
          result.put(barcode);
          result.put("");
          result.put("");
          CallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));

          Log.d("AndroidScanner", "Barcode read: " + barcode);
        }
      } else {
        String err = p_Data.getParcelableExtra("err");
        JSONArray result = new JSONArray();
        result.put(err);
        result.put("");
        result.put("");
        CallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, result));
      }
    }
  }

  @Override
  public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
    CallbackContext = callbackContext;
  }

  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
  private void openNewActivity(Context context, JSONArray args) {
    Activity activity = cordova.getActivity();

    Intent intent = new Intent(context, SecondaryActivity.class);
    intent.putExtra("DetectionTypes", args.optInt(0, 1234));
    intent.putExtra("ViewFinderWidth", args.optDouble(1, .5));
    intent.putExtra("ViewFinderHeight", args.optDouble(1, .7));
    intent.putExtra("Orientation", activity.getRequestedOrientation());

    String[] ignoreCodes = new String[args.length()-3];
    for (int i = 3; i < args.length(); i++) {
      ignoreCodes[i] = args.optString(i);
    }
    intent.putExtra("IgnoreCodes", ignoreCodes);

    this.cordova.setActivityResultCallback(this);
    this.cordova.startActivityForResult(this, intent, RC_BARCODE_CAPTURE);
  }

  // ----------------------------------------------------------------------------
  // |  Helper classes
  // ----------------------------------------------------------------------------
  private class OneShotTask implements Runnable {
    private Context   _Context;
    private JSONArray _Args   ;

    private OneShotTask(Context p_Context, JSONArray p_TaskArgs) {
      _Context = p_Context;
      _Args = p_TaskArgs;
    }

    public void run() {
      openNewActivity(_Context, _Args);
    }
  }
}
