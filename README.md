# MultiApp

![](https://img.shields.io/badge/license-AGPL3.0-brightgreen.svg?style=flat)
![](https://img.shields.io/badge/Android-7.0%20--%2013-blue.svg?style=flat)
![](https://img.shields.io/badge/arch-armeabi--v7a%20%7C%20arm64--v8a-blue.svg?style=flat)

[中文](docs/READEME-zh-CN.md)

## whatsapp group
![image](docs/res/multiapp_whatsapp.png)

## Project Introduction
MultiApp is a virtual Android container, which can open more apps. This project provides a simple UI for you to experience. You can also download the official apk from [Google play](https://play.google.com/store/apps/details?id=com.waxmoon.ma.gp) to enjoy a better experience. If you are an android developer, you can also customize your own UI. You don't need to pay attention to the technical details that are difficult to understand. You can use the api provided by [opensdk](https://github.com/WaxMoon/opensdk) to open more apps. If you have any questions during the experience, you can contact us by WeChat.

In addition, we will continue to fix problems and update opensdk code to provide you with the best experience.

### **You can watch the following videos to understand our capabilities**

[Without install Google play to enjoy](https://github.com/WaxMoon/MultiApp/blob/5fc33308ca9fd651ce7be2a5bab53160d5303426/docs/res/github_gp.mp4) <----> [Multiple open facebook](https://github.com/WaxMoon/MultiApp/blob/5fc33308ca9fd651ce7be2a5bab53160d5303426/docs/res/github_fb.mp4)


https://user-images.githubusercontent.com/118731992/204190831-4bd2c0db-6682-48d8-aa4f-1e5b5756650e.mp4


https://user-images.githubusercontent.com/118731992/204192759-14984680-e5ed-4685-a0c7-bebff3d8a7b0.mp4

<div align=center>
    <video width="320" height="320" controls>
        <source src="docs/res/github_gp.mp4" type="video/mp4">
    </video>
    <video width="320" height="320" controls>
        <source src="docs/res/github_fb.mp4" type="video/mp4">
    </video>
</div>

## Principle Introduction
The traditional multi-opening solution relies on java dynamic proxy, inline hook, proxy forwarding and others to ensure app running in the virtual process. If the third-party app also uses the java dynamic proxy, there will be a problem because of the proxies will overlap each other, which will cause the third-party app's code logic to change when it is running. Changes in logic "being" are so crazy, and may affect their earnings. I think traditional solutions cannot be defined as containers.

At the beginning of MultiApp technology selection, java dynamic proxy was abandoned, and binder components such as service, receiver, and provider are all maintained by MultiApp engine itself. It's a pity that the Activity component must maintain its life cycle through a proxy, but we use a more reliable solution to ensure that it does not affect the code logic of the app. In addition, we have developed a more effective svc hook solution based on seccomp/bpf in native hook technology, and will enabled it in some scenarios such as 360 shell app. In general, we are closer to the sandbox concept.

### Below I made two simple demos

**1) Print the code running stack in the Activity.onCreate function, and use the traditional software, MultiApp, and android-system to open**

```Java
public class MainActivity extends ButtonActivity {

    final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Exception().printStackTrace();
    }
}
```
The code stack of the first picture is abnormal because of app running in traditional multi-open software, the second picture is the code stack run by MultiApp and the third is android system.

![image](docs/res/stack_other.png)

![image](docs/res/stack_MultiApp.png)

![image](docs/res/stack_system.png)

**2) Determine whether the binder interface of ActivityManager is dynamically proxied, and run in traditional multi-open software and MultiApp**

```Java
public class MainActivity extends ButtonActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Exception().printStackTrace();

        {
            try {
                Class<?> class_ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");

                Method method_getDefault = class_ActivityManagerNative.getDeclaredMethod("getDefault");

                IInterface remote_ActivityManager = (IInterface) method_getDefault.invoke(null);

                boolean isProxy = remote_ActivityManager instanceof Proxy;

                Log.d("WaxMoon", String.format("ActivityManager(%s) is proxy: %s", remote_ActivityManager, isProxy));

            } catch (Exception ignore) {

            }
        }
    }
}
```

This is the log of traditional multi-open software
```Text
11-25 17:56:38.823  5153  5153 D WaxMoon : ActivityManager(android.app.IActivityManager$Stub$Proxy@8abaec7) is proxy: true
```

This is the log of MultiApp
```Texx
11-25 17:59:13.804  8197  8197 D WaxMoon : ActivityManager(android.app.IActivityManager$Stub$Proxy@79f3e55) is proxy: false
```

## Quick Start
### Download code
Due to the use of submodule, you have to pull subrepos manually
```shell
git clone https://github.com/WaxMoon/MultiApp.git
git submodule update --init
```

### extends HackApplication
HackApplication will help you complete the initialization of the engine.
```Java
public class MoonApplication extends HackApplication {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
```
You can also refer to the opensdk code to initialize the engine yourself.
```Java
public class HackApplication extends Application {

    private static final boolean DEBUG = Features.DEBUG;
    private static final String TAG = HackApplication.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (DEBUG) Log.d(TAG, "attachBaseContext start");
        HackRuntime.install(this, "version", true);
        Cmd.INSTANCE().exec(CmdConstants.CMD_APPLICATION_ATTACHBASE, this, base);
        if (DEBUG) Log.d(TAG, "attachBaseContext end");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate start");
        Cmd.INSTANCE().exec(CmdConstants.CMD_APPLICATION_ONCREATE);
        if (DEBUG) Log.d(TAG, "onCreate end");
    }
}
```

### Use HackApi.installPackageFromHost to install app
```Kotlin
var install: (ApkInfo)->Unit = { apkInfo ->
    val ret = HackApi.installPackageFromHost(apkInfo.pkgName, userSpace, false)

    when (ret) {
        INSTALL_SUCCEEDED ->
            Toast.makeText(MoonApplication.INSTANCE(), R.string.toast_success,
                Toast.LENGTH_SHORT).show()
        INSTALL_FAILED_ALREADY_EXISTS ->
            Toast.makeText(MoonApplication.INSTANCE(), R.string.toast_already_installed,
                Toast.LENGTH_SHORT).show()
        else ->
            Toast.makeText(MoonApplication.INSTANCE(), R.string.toast_fail, Toast.LENGTH_SHORT).show()
    }
}
```

### You can also call HackApi.installApkFiles to install apk with apk file path

```Java
    /**
     * install the apk/apks that is not installed on the system.
     * @param apkPathOrDir if this app is a full apk file, apkPathOrDir should pass an absolute path,
     *                     such as /sdcard/com.xx.yy/com.xx.yy.apk;
     *
     *                     if this app is split apk files, apkPathOrDir should pass the directory
     *                     containing all the apk files for this app, such as /sdcard/com.xx.yy/,
     *                     and this directory can't contain apk files that do not belong to the current app.
     *
     * @param userId
     * @param forceInstall
     * @return public static final int INSTALL_SUCCEEDED = 1;
     */
    public static int installApkFiles(String apkPathOrDir, int userId, boolean forceInstall) {
        ...
    }
```

### Use HackApi.startActivity to run app

```Kotlin
var startApp: (ApkInfo)->Unit = { apkInfo ->
    var intent:Intent? = null
    if (apkInfo.sysInstalled) {
        intent = MoonApplication.INSTANCE().packageManager.getLaunchIntentForPackage(apkInfo.pkgName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    } else {
        Toast.makeText(MoonApplication.INSTANCE(), R.string.toast_unsupport, Toast.LENGTH_SHORT).show()
    }

    if (intent != null) {
        Log.d(TAG, "begin start " + apkInfo.pkgName)
        val startRet = HackApi.startActivity(intent, userSpace)
        if (startRet != START_SUCCESS) {
            Toast.makeText(MoonApplication.INSTANCE(), R.string.toast_fail, Toast.LENGTH_SHORT).show()
        }
    }
}
```

### Use HackApi.registerApplicationCallback to monitor Application lifecycle

```Java
    private void monitorApplicationEvent() {
        final String TAG = "EVENT";
        HackApi.registerApplicationCallback(new HackApi.ApplicationCallback() {
            @Override
            public void onInitAppContext(Object loadedApk, Context appContext) {
                Slog.d(TAG, "onInitAppContext %s %s", loadedApk, appContext);
            }

            @Override
            public void onAttachBaseContext(Application app) {
                Slog.d(TAG, "onAttachBaseContext %s", app);
            }

            @Override
            public void onInstallProviders(Application app) {
                Slog.d(TAG, "onInstallProviders %s", app);
            }

            @Override
            public void onCreate(Application app) {
                Slog.d(TAG, "onCreate %s", app);
            }
        });
    }
```

## Features
*  support android7-android13(android7-8 is under development)
*  support armv7-32, armv8-64
*  provide master pkg/assist pkg
*  support google play
*  multi-open app
*  without install to enjoy

## Business Features
*  Function customization
*  Business license
*  others...


## License Notes
Both this project and opensdk use the AGPL-3.0 license. Before publishing your software, please let us know your thoughts. In some cases, you can use it freely.

## Safety Notes
From the perspective of code security and industry security, we **disabled the software debugging** function. If you have relevant legal needs, you can contact by wechat.

## Others
If you see advertisements when using the software, this is a normal phenomenon. This is our motivation to constantly maintain github. I hope you will understand.

## Contact Details
Wechat: WaxMoon2018
Email: cocos_sh@sina.com

## Change Log

### 2022/12

1.optimize bpf/seccomp

2.fix roc crash

3.fix messager start timeout

4.fix can't run MultiAccount-app

5.fix lilith-game crash

6.fix bilibili crash

7.fix app can't login by Google(only support debuggable="false")

8.support more 360shell apps

9.support ijm shell app

10.fix Google play login issue

11.fix youlin crash

12.fix snapchat can't send msg

13.fix paltalk crash

14.support Application lifecycle callback(HackApi.registerApplicationCallback)

15.support tiktok