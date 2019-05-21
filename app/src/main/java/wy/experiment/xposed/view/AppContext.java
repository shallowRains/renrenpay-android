package wy.experiment.xposed.view;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.multidex.MultiDex;
import android.util.Log;

import com.tencent.bugly.crashreport.CrashReport;
import com.zhy.http.okhttp.OkHttpUtils;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import wy.experiment.xposed.activity.MainActivity;
import wy.experiment.xposed.activity.WelcomeActivity;
import wy.experiment.xposed.db.model.AppsList;
import wy.experiment.xposed.db.model.User;
import wy.experiment.xposed.db.util.UserDao;
import wy.experiment.xposed.hook.HookBase;
import wy.experiment.xposed.hook.HookList;
import wy.experiment.xposed.hook.ReceiverMain;
import wy.experiment.xposed.tool.AccountManager;
import wy.experiment.xposed.utils.ConfigManager;

/**
 * Created by chenxinyou on 2019/3/13.
 */

public class AppContext extends Application {
    private static AppContext instance;
    private AccountManager accountManager;
    private boolean showWelcome = true;
    private static final String TAG = "cxyApp";
    //    private IWXAPI msgApi;
    private User user;
    private AppsList appsList;
    private ConfigManager configManager;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AppsList getAppsList() {
        return appsList;
    }

    public void setAppsList(AppsList appsList) {
        this.appsList = appsList;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public static AppContext getInstance() {
        return instance;
    }

    public boolean isShowWelcome() {
        return showWelcome;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        configManager = ConfigManager.getInstance();
        CrashReport.initCrashReport(getApplicationContext(), "7b37c18319", false);
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                //其他配置
                .build();
        String processAppName = getAppName(android.os.Process.myPid());
        // 如果APP启用了远程的service，此application:onCreate会被调用2次
        // 为了防止被初始化2次，加此判断会保证SDK被初始化1次
        // 默认的APP会在以包名为默认的process name下运行，如果查到的process name不是APP的process name就立即返回
        if (processAppName == null || !processAppName.equalsIgnoreCase(this.getPackageName())) {
            // 则此application::onCreate 是被service 调用的，直接返回
            return;
        }

        instance = this;
        // 程序崩溃时触发线程  以下用来捕获程序崩溃异常并重启应用
        Thread.setDefaultUncaughtExceptionHandler(restartHandler);
        HookList.getInstance();
        registerReceiver(new ReceiverMain(), new IntentFilter(HookBase.RECV_ACTION));
        OkHttpUtils.initClient(okHttpClient);
        accountManager = new AccountManager();
        Log.d(TAG, accountManager.toString());
        int id = accountManager.getUserId();
        if(id == 0) {
            id = configManager.getInt("id", 0);
        }
        if (accountManager.isLogined()) {
            User user = UserDao.findById(id);

            if(user != null)
                setUser(user);
            else
                accountManager.setLogined(false);
        }
    }

    public void updateUser(User user) {
        UserDao.update(user);
        setUser(user);
    }

    public void userLogin(int id) {
        Log.d(TAG, "id -> " + id);
        configManager.putInt("id", id);
    }

    /**
     * 获取APP的进程名
     *
     * @param pID
     * @return
     */
    private String getAppName(int pID) {
        String processName = null;
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List l = am.getRunningAppProcesses();
        Iterator i = l.iterator();
        PackageManager pm = this.getPackageManager();
        while (i.hasNext()) {
            ActivityManager.RunningAppProcessInfo info = (ActivityManager.RunningAppProcessInfo) (i.next());
            try {
                if (info.pid == pID) {
                    processName = info.processName;
                    return processName;
                }
            } catch (Exception e) {
                // Log.d("Process", "Error>> :"+ e.toString());
            }
        }
        return processName;
    }

    /**
     * 创建服务用于捕获崩溃异常
     */
    private Thread.UncaughtExceptionHandler restartHandler = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable ex) {
            restartApp();//发生崩溃异常时,重启应用
        }
    };

    /**
     * 重启此应用
     */
    private void restartApp() {
        Intent intent = new Intent(instance, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("auto", true);
        instance.startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());  //结束进程之前可以把你程序的注销或者退出代码放在这段代码之前
    }
}
