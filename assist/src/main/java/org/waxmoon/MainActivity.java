package org.waxmoon;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.hack.assist.AssistProvider;
import com.hack.opensdk.BuildConfig;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isTaskRoot() && !TextUtils.equals(BuildConfig.MASTER_PACKAGE, getPackageName())) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(BuildConfig.MASTER_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                //ignore
            }
            finishAndRemoveTask();
        } else {
            try {
                ProviderInfo info = getPackageManager().getProviderInfo(new ComponentName(this, AssistProvider.class), 0);
                Intent intent = new Intent();
                intent.setData(new Uri.Builder().scheme("content").authority(info.authority).build());
                //关联启动
                setResult(Activity.RESULT_OK, intent);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            finish();
        }

    }
}
