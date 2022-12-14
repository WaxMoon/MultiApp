package org.waxmoon

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import org.waxmoon.ui.theme.GithubMultiAppTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hack.opensdk.BuildConfig
import com.hack.opensdk.CmdConstants
import com.hack.opensdk.HackApi
import com.hack.utils.FileUtils
import java.io.File
class AppListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GithubMultiAppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    AppLayout()
                }
            }
        }
    }
}

const val INSTALL_SUCCEEDED = 1
const val INSTALL_FAILED_ALREADY_EXISTS = -1
const val INSTALL_FAILED_INVALID_APK = -2
const val INSTALL_FAILED_INVALID_URI = -3

const val START_SUCCESS = 0

const val MENU_ENABLE_GP = 0
const val MENU_OPEN_GP = 1

val TAG = AppListActivity::class.simpleName
var userSpace: Int = 0
var dialogState = mutableStateOf(Any())


var assistInstallRequestContract = object : ActivityResultContract<ApkInfo, ApkInfo>() {
    var info: ApkInfo? = null

    override fun parseResult(resultCode: Int, intent: Intent?): ApkInfo {
        return info!!
    }

    override fun createIntent(context: Context, input: ApkInfo): Intent {
        info = input
        return Intent().apply {
            setDataAndType(requestInstallAssist(context), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION.or(Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            action = Intent.ACTION_VIEW
        }

    }

    fun requestInstallAssist(context: Context): Uri? {
        val file = File(context.externalCacheDir, "assist.apk")
        FileUtils.extractAsset(context, "assist.apk", file)
        val authority = context.packageName + ":" + MoonProvider::class.qualifiedName
        val uri = FileProvider.getUriForFile(context, authority, file)
        return uri
    }
}

fun isInstall(info: ApkInfo): Boolean {
    return try {
        MoonApplication.INSTANCE().packageManager.getPackageInfo(info.getShellPackage(), 0)
        true
    } catch (e: Exception) {
        false
    }
}


@Composable
fun AssistInstallDialog() {
    val openDialog = remember { dialogState }
    val launcher = rememberLauncherForActivityResult(contract = assistInstallRequestContract) {
        if (!isInstall(it)) {
            Toast.makeText(
                MoonApplication.INSTANCE(), R.string.toast_fail,
                Toast.LENGTH_SHORT
            ).show()
        }

    }
    if (openDialog.value is ApkInfo) {
        AlertDialog(
            onDismissRequest = {
                // Dismiss the dialog when the user clicks outside the dialog or on the back
                // button. If you want to disable that functionality, simply use an empty
                // onCloseRequest.
                openDialog.value = Any()
            },
            title = {
                Text(text = "Hint")
            },
            text = {
                Text(
                    "need install assist package "
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        launcher.launch(openDialog.value as ApkInfo)
                        openDialog.value = Any()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        openDialog.value = Any()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AppLayout() {
    Scaffold(
        topBar = { MyAppBar() },
        content = { innerPadding ->
            BodyContent(Modifier.padding(innerPadding))
            AssistInstallDialog()
        }
    )
}

@Composable
fun MyAppBar() {
    var userId by rememberSaveable { mutableStateOf(userSpace) }
    var menuExpand = remember { mutableStateOf(false) }
    var menus = listOf(stringResource(R.string.menu_enable_gp), stringResource(R.string.menu_open_gp))
    TopAppBar(
        title = { Text(stringResource(R.string.app_list)) },
        actions = {
            IconButton(
                onClick = {
                    if (userId > 0) {
                        userId = --userSpace
                    }
                }
            ) {
                Icon(Icons.Filled.ArrowLeft, contentDescription = null)
            }
            Text(text = stringResource(R.string.text_space) + userId, Modifier.padding(3.dp))
            IconButton(
                onClick = {
                    userId = ++userSpace
                }
            ) {
                Icon(Icons.Filled.ArrowRight, contentDescription = null)
            }

            IconButton(onClick = {
                menuExpand.value = true
            }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Open Options"
                )
            }

            DropdownMenu(expanded = menuExpand.value, onDismissRequest = { menuExpand.value = false }) {
                menus.forEachIndexed { index, s ->
                    DropdownMenuItem(onClick = {
                        menuExpand.value = false
                        clickMenu(index)
                    }) {
                        Text(text = s)
                    }
                }
            }
        }
    )
}

@Composable
fun BodyContent(modifier: Modifier = Modifier) {
    ApkList(modifier)
}

@Composable
fun ApkList(modifier: Modifier = Modifier) {
    val list = remember { mutableStateListOf<ApkInfo>() }
    val context = LocalContext.current
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    val activityInfos = pm.queryIntentActivities(intent, 0)
    for (resolveInfo in activityInfos) {
        if (!context.packageName.equals(resolveInfo.activityInfo.packageName)) {
            val apkInfo = ApkInfo(resolveInfo.activityInfo.applicationInfo.sourceDir, true)
            apkInfo.init(
                context = context,
                pkg = resolveInfo.activityInfo.applicationInfo.packageName
            )
            if (apkInfo.valid()) {
                Log.d(TAG, "ApkInfo: $apkInfo")
                list.add(apkInfo)
            }
        }
    }

    LazyColumn(modifier.fillMaxSize(1.0f)) {
        items(list.size) {index->
            ApkItem(apkInfo = list.get(index))
        }
    }
}

@Composable
fun ApkItem(modifier: Modifier = Modifier, apkInfo: ApkInfo) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(5.dp)
    ) {
        //ApkBaseInfo
        Row(modifier.fillMaxWidth(1.0f)) {
            Image(
                bitmap = apkInfo.bitmap,
                contentDescription = null,
                modifier = modifier
                    .padding(5.dp)
                    .clip(CircleShape)
                    .size(60.dp)
                    .border(2.dp, MaterialTheme.colors.secondaryVariant, CircleShape)
            )
            Spacer(modifier = modifier.width(10.dp))

            Column(
                modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(text = apkInfo.label, fontWeight = FontWeight.Bold)
                Spacer(modifier = modifier.padding(3.dp))
                Log.d(TAG, "ApkInfo.pkgName: ${apkInfo.pkgName}")
                Text(
                    text = "${stringResource(id = R.string.item_pkg_name)}${apkInfo.pkgName}",
                    color = MaterialTheme.colors.secondaryVariant)
            }

            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription =
                    if (isExpanded)
                        stringResource(R.string.item_show_less)
                    else
                        stringResource(R.string.item_show_more)
                )
            }
        }
        //detailInfo
        if (isExpanded) {
            Row(
                modifier = modifier
                    .padding(10.dp)
                    .fillMaxWidth(1.0f),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { uninstall(apkInfo) }) {
                    Text(
                        text = stringResource(id = R.string.item_btn_uninstall),
                        color = Color.Gray
                    )
                }
                TextButton(onClick = { install(apkInfo) }) {
                    Text(text = stringResource(id = R.string.item_btn_install))
                }

                TextButton(onClick = {
                    if (TextUtils.equals(BuildConfig.ASSIST_PACKAGE, apkInfo.getShellPackage())) {
                        if (!isInstall(apkInfo)) {
                            dialogState.value = apkInfo
                            return@TextButton
                        }
                    }
                    startApp(apkInfo)
                }) {
                    Text(
                        text = stringResource(id = R.string.item_btn_start),
                        color = MaterialTheme.colors.secondary
                    )
                }
            }
        }
    }
}

var clickMenu: (Int)->Unit = { index->
    when (index) {
        MENU_ENABLE_GP -> {
            var googlePkgs =
                listOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")
            for (pkg in googlePkgs) {
                HackApi.installPackageFromHost(pkg, userSpace, false)
            }
        }
        MENU_OPEN_GP -> {
            var intent:Intent?
            intent = MoonApplication.INSTANCE().packageManager.getLaunchIntentForPackage("com.android.vending")
            intent?.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            HackApi.startActivity(intent, userSpace)
        }
    }
}

var uninstall: (ApkInfo)->Unit = { apkInfo ->
    HackApi.uninstallPackage(apkInfo.pkgName, userSpace)
}

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

@Stable
class ApkInfo constructor(val apkPath: String, val sysInstalled: Boolean) {
    lateinit var label: String
    lateinit var pkgName: String
    lateinit var version: String
    lateinit var bitmap: ImageBitmap
    //系统app，比如通讯录，设置，相机等，暂不支持
    var sysApp: Boolean = false
    var extras: Bundle? = null


    fun getShellPackage():String{
        if (extras == null) {
            extras = HackApi.getPackageSetting(pkgName,userSpace, 0)
        }
        val assist: Boolean? = extras?.getBoolean(CmdConstants.PKG_SET_REQUEST_ASSISTANT, false)
        return if (assist != null && assist) BuildConfig.ASSIST_PACKAGE else BuildConfig.MASTER_PACKAGE
    }

    fun init(context: Context, pkg: String?) {
        val pm = context.packageManager
        val pkgInfo: PackageInfo?
        if (sysInstalled && pkg != null) {
            pkgInfo = pm.getPackageInfo(pkg, 0)
            val labelId: Int = pkgInfo.applicationInfo.labelRes
            when (labelId) {
                0 -> { label = pkgInfo.packageName}
                else -> {
                    label = pm.getResourcesForApplication(pkgInfo.packageName).getString(labelId)
                }
            }
        } else {
            pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
            label = pkgInfo?.packageName ?: ""
        }
        if (pkgInfo != null) {
            pkgInfo.applicationInfo.publicSourceDir
            pkgName = pkgInfo.packageName
            version = pkgInfo.versionName
            val icon = pkgInfo.applicationInfo.loadIcon(pm)
            bitmap = getAppIconCompat(icon)
            sysApp = (pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        }
    }

    fun valid(): Boolean {
        return !pkgName.isEmpty() && !version.isEmpty() && !sysApp
    }

    private fun getAppIconCompat(icon: Drawable) : ImageBitmap {
        val bitmap = Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas()
        tmpCanvas.setBitmap(bitmap)
        icon.setBounds(0, 0, tmpCanvas.width, tmpCanvas.height)
        icon.draw(tmpCanvas)
        tmpCanvas.setBitmap(null)
        return bitmap.asImageBitmap()
    }

    override fun toString(): String {
        return "ApkInfo(apkPath='$apkPath', sysInstalled=$sysInstalled, " +
                "label='$label', pkgName='$pkgName', " +
                "version='$version', bitmap=$bitmap, sysApp=$sysApp)"
    }

}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GithubMultiAppTheme {
        Greeting("Android")
    }
}