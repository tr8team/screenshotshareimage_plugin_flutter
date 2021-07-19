package mt.innovation.screenshotshareimage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.renderer.FlutterRenderer;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/** ScreenshotShareImagePlugin */
public class ScreenshotShareImagePlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

  private static final String TAG = "ScreenshotShareImagePlugin";

  private Context context;
  private FlutterRenderer renderer;
  private MethodChannel channel;

  @Nullable
  private Activity activity;

  private final int WRITE_ACCESS_REQUEST_ID = 12;

  public static void setup(ScreenshotShareImagePlugin plugin, Context context, FlutterRenderer renderer, MethodChannel channel) {
    plugin.context = context;
    plugin.renderer = renderer;
    plugin.channel = channel;
    plugin.channel.setMethodCallHandler(plugin);
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "screenshot_share_image");
    ScreenshotShareImagePlugin.setup(
        this,
        binding.getApplicationContext(),
        binding.getFlutterEngine().getRenderer(),
        channel
    );
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {

  }


  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    setRequestPermissionListener(binding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() { }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) { }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }

  private void setRequestPermissionListener(@NonNull final ActivityPluginBinding binding) {
    if (activity == null) return;

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      binding.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
        @Override
        public boolean onRequestPermissionsResult(int i, String[] strings, int[] ints) {
          if (i == WRITE_ACCESS_REQUEST_ID) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                takeScreenshot("screenshot_" + System.currentTimeMillis());
                return true;
              } else if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                try {
                  new AlertDialog.Builder(activity)
                      .setTitle("Storage Permission")
                      .setMessage("We require the storage permission to create and store the screenshots.")
                      .setCancelable(true)
                      .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                          Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                          Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                          intent.setData(uri);
                          activity.startActivity(intent);
                        }
                      })
                      .show();
                } catch (Exception exception) {
                  // We do nothing here
                }
              } else {
                activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
                return false;
              }
            }
          }
          return false;
        }
      });
    }

    // Else, we do nothing for Android 30+
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("takeScreenshotAndShare")) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        takeScreenshotWithoutExternalStorage("screenshot_" + System.currentTimeMillis());
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (activity != null) {
          activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_ACCESS_REQUEST_ID);
        }
      } else {
        takeScreenshot("screenshot_" + System.currentTimeMillis());
      }

    } else {
      result.notImplemented();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  private void takeScreenshotWithoutExternalStorage(String fileName) {
    try {
      ContentValues contentValues = new ContentValues();

      contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName + ".jpg");
      contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
      contentValues.put(
          MediaStore.Images.Media.RELATIVE_PATH,
          Environment.DIRECTORY_PICTURES + "/" + "Gotrade"
      );

      Bitmap bitmap = renderer.getBitmap();

      Uri uri = checkIfUriExistOnPublicDirectory(fileName);
      if (uri ==  null) {
        uri = context.getContentResolver().insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        );
      }

      OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
      File imageFile = new File(getPathFromURI(uri));

      int quality = 100;
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
      outputStream.flush();
      outputStream.close();

      openScreenshot(imageFile);

    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  public Uri checkIfUriExistOnPublicDirectory(String fileName) {
    ContentResolver resolver = context.getContentResolver();
    String[] projections = {
        MediaStore.MediaColumns._ID,
    };

    String selection = MediaStore.MediaColumns.DISPLAY_NAME +
        "='" + fileName + ".jpg" + "'";

    Cursor cur = resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projections,
        selection,
        null,
        null
    );

    if (cur != null) {
      if (cur.getCount() > 0) {
        if (cur.moveToFirst()) {
          long id = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));

          return ContentUris.withAppendedId(
              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              id
          );
        }
      }

      cur.close();
    }

    return null;
  }

  private void takeScreenshot(String fileName) {
    try {
      String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/Gotrade/";
      File dirFile = new File(directoryPath);

      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }

      // image naming and path  to include sd card  appending name you choose for file
      String mPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/Gotrade/" + fileName + ".jpg";
      Bitmap bitmap = renderer.getBitmap();

      File imageFile = new File(mPath);

      FileOutputStream outputStream = new FileOutputStream(imageFile);
      int quality = 100;
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
      outputStream.flush();
      outputStream.close();

      openScreenshot(imageFile);

    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void openScreenshot(File imageFile) {
    StrictMode.VmPolicy.Builder builder = null;
    builder = new StrictMode.VmPolicy.Builder();
    StrictMode.setVmPolicy(builder.build());

    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_SEND);
    Uri uri = Uri.fromFile(imageFile);
    intent.putExtra(Intent.EXTRA_STREAM, uri);
    intent.setType("image/*");
    if (activity != null) {
      activity.startActivity(
          Intent.createChooser(intent, "Share Screenshot")
      );
    }
  }

  private String getPathFromURI(Uri uri) {
    String[] projection = { MediaStore.Images.Media.DATA };
    Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

    // Sanity check
    if (cursor == null) return null;

    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
    cursor.moveToFirst();
    String path = cursor.getString(columnIndex);
    cursor.close();
    return path;
  }
}
