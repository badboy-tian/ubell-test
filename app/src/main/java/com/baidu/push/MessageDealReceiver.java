package com.baidu.push;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.ubia.util.DateUtil;
import com.xiaomi.mipush.sdk.MiPushMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import cn.ubia.LiveViewGLviewActivity;
import cn.ubia.MainActivity;
import cn.ubia.UBell.R;
import cn.ubia.UbiaApplication;
import cn.ubia.base.Constants;
import cn.ubia.bean.AlarmMessage;
import cn.ubia.bean.DeviceInfo;
import cn.ubia.db.DatabaseManager;
import cn.ubia.fragment.MainCameraFragment;
import cn.ubia.util.PreferenceUtil;


public class MessageDealReceiver extends BroadcastReceiver{

    String TAG = "MessageDealReceiver";

    private static MessageDealReceiver messageDealReceiver;

    public void onReceive(Context context, Intent intent) {
//    String action = intent.getAction();
//    String messageuid = intent.getExtras().getString("messageuid");
//    String event = intent.getExtras().getString("event");
//
//    Log.i(TAG,"MessageDealReceiver action:"+action + " uid:" + messageuid + " event:"+event);
//    if (action.equals("action.MessageDealReceiver"))
//    {
//        if(messageuid != null && messageuid.length() > 0) {
//            onReceivePassThroughMessage(context, messageuid, event);
//        }
//    }
    }


    public static MessageDealReceiver getInstance(){
        if(messageDealReceiver == null){
            messageDealReceiver = new MessageDealReceiver();
        }

        return messageDealReceiver;
    }

    private boolean isRunningForeground (Context context)
    {
        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        String currentPackageName = cn.getPackageName();
        if(!TextUtils.isEmpty(currentPackageName) && currentPackageName.equals(UbiaApplication.getInstance()
                .getApplicationContext().getPackageName()))
        {
            return true ;
        }

        return false ;
    }

    public void alarmInfoCallBack(Context context, String title, String uid,
                                  String time) {

        AlarmMessage alarmMessage = new AlarmMessage(title, uid, time);
        Intent activityIntent = new Intent(context, MainActivity.class) .putExtra("alarmMessage", alarmMessage);
        activityIntent.setClass(context, MainActivity.class);
        activityIntent.putExtra("alarmMessage", alarmMessage);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);



        Log.d(TAG, "alarmInfoCallBack  ,context ="+context);
    }

    public boolean callphoneInfoCallBack(Context context, String title, String uid,
                                         String time,String event) {
     //   Log.e(TAG, "guo alarmInfoCallBack  , UbiaApplication.currentDeviceLive ="+ UbiaApplication.currentDeviceLive);
   //     Log.e(TAG, "guo alarmInfoCallBack  , event ="+event+",time="+time);
        if ( UbiaApplication.currentDeviceLive.equals("") &&( !MainCameraFragment.getRunningActivityName(LiveViewGLviewActivity.class.getSimpleName())|| LiveViewGLviewActivity.isBackgroundRunning)){//&&  ( System.currentTimeMillis()- timeLast>delaycalltime)) {//no live view

            UbiaApplication.fromReceiver = true;
            UbiaApplication.messageUID = uid;
            UbiaApplication.messageTime = time;
            UbiaApplication.messageEvent = event; //这里要保存，不能直接传到下个activity，否则会接收到null,原因暂不明
            Intent activityIntent = new Intent(context, MainActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
            return false ;
        }
     //   if(!this.time.equals(time) &&  ( System.currentTimeMillis()- timeLast>delaycalltime)){
            Intent intent = new Intent( "action.newDeviceCallBroadcastReceiver" );
//			uid = "LFLDI6LJ2G3VXXU3FU5Q";
            intent.putExtra("alarmMessageuid", uid);
            context.sendBroadcast(intent);
            //Log.d(TAG, "alarmInfoCallBack ,newDeviceCallBroadcastReceiver ,time =" + time);
           // this.time = time;

     //   }
        return true;
    }

    long lastTime = 0;
    String lastEvent = "";

    static long timeLast   = 0;
    static long  timeLastnotify = 0;
    int delaycalltime = 3000;

    Map<String,String> pushDeviceMap = new HashMap();
    public void onReceivePassThroughMessage(Context context, String messuid, String event,String timestamp) {

        Log.e("guo. onReceivePassThroughMessage",pushDeviceMap.get(messuid)+"：timestamp:"+timestamp);
         if(pushDeviceMap.get(messuid)!=null){
            lastEvent =  pushDeviceMap.get(messuid).split("-")[0];
            lastTime = Long.valueOf( pushDeviceMap.get(messuid).split("-")[1]);
            if(lastEvent.equals(event)){
                if(Long.valueOf(timestamp)<=lastTime){
            return;
        }
            }
          }

        Log.e("guo..start onReceivePassThroughMessage","timestamp:"+timestamp);



        boolean showactivitycall = true; // true default
        int currentmessageType = PreferenceUtil.getInstance().getInt(   Constants.MESSAGETYPE_CHECK + messuid, UbiaApplication.DefaultReceiverType);

        Log.e(TAG,"onReceivePassThroughMessage is called. messuid:" + messuid +"  event:"+event +"  currentmessageType："+currentmessageType+"  timestamp："+timestamp+"  lasttime："+lastTime);

       /* if(event.equals("plug")){
            currentmessageType = 1;
        }*/

        if(currentmessageType == 0){

            return;
        }else if (currentmessageType > 1) {// 来电呼叫
            boolean hasfoundUID = false ;
            DeviceInfo deviceInfo = null;
            DatabaseManager var1 = new DatabaseManager( UbiaApplication  .getInstance().getApplicationContext());
            SQLiteDatabase var2 = var1.getReadableDatabase();
            Cursor var3 = var2.query("device", new String[] { "_id",
                            "dev_nickName", "dev_uid", "dev_name", "dev_pwd", "view_acc",
                            "view_pwd", "event_notification", "camera_channel", "snapshot",
                            "ask_format_sdcard", "camera_public" , "installmode","hardware_pkg"},
                    (String) null, (String[]) null, (String) null, (String) null,
                    "_id LIMIT 50");
            String devname = "null" ;
            long _id  = 0  ;
            String dev_nickName = "" ;
            String dev_uid = "" ;
            String view_acc = "" ;
            String view_pwd  = "";
            int event_notification  = 0  ;
            int camera_channel  = 0 ;
            byte[] var12  ;
            int var13  = 0  ;
            int ispublic  = 0  ;
            while (var3.moveToNext()) {
                _id = var3.getLong(0);
                dev_nickName = var3.getString(1);
                dev_uid = var3.getString(2);
                view_acc = var3.getString(5);
                view_pwd = var3.getString(6);
                event_notification = var3.getInt(7);
                camera_channel = var3.getInt(8);
                var12 = var3.getBlob(9);
                var13 = var3.getInt(10);
                ispublic = var3.getInt(11);

                int installmode = var3.getInt(12);
                int hardware_pkg = var3.getInt(13);
                if(dev_uid.equals(messuid))
                {

                    deviceInfo = new DeviceInfo(_id, dev_uid, dev_nickName,
                            dev_uid, dev_uid, view_pwd, "", event_notification, camera_channel, null);
                    // Log.i("IOTCamera", "DeviceInfo.size()>>>>>>>>>>>" + var16);
                    deviceInfo.installmode = installmode;
                    deviceInfo.connect_count = 0;

                    pushDeviceMap.put(messuid,event+"-"+timestamp);
                    hasfoundUID= true;
                    break;
                }
            }
            if(!hasfoundUID)
            {
                Log.v("deviceinfo", " 推送未能在本地找到相关设备 "  );
                return;
            }
            if(deviceInfo==null){
                return;
            }

            MainCameraFragment.getAllRunningActivityName("");
            if (!TextUtils.isEmpty(messuid)  && showactivitycall) {


                callphoneInfoCallBack(UbiaApplication.getInstance()
                                .getApplicationContext(), "电话来了",
                        messuid,
                        DateUtil.formatNormalTimeStyle(System
                                .currentTimeMillis()),event);
             //   timeLast = System.currentTimeMillis();
            }
        }else{
            boolean hasfoundUID = false ;

            try {

             //   if(  System.currentTimeMillis()- timeLastnotify >delaycalltime){
                    DeviceInfo deviceInfo = null;
                    DatabaseManager var1 = new DatabaseManager( UbiaApplication
                            .getInstance().getApplicationContext());
                    SQLiteDatabase var2 = var1.getReadableDatabase();
                    Cursor var3 = var2.query("device", new String[] { "_id",
                                    "dev_nickName", "dev_uid", "dev_name", "dev_pwd", "view_acc",
                                    "view_pwd", "event_notification", "camera_channel", "snapshot",
                                    "ask_format_sdcard", "camera_public" , "installmode","hardware_pkg"},
                            (String) null, (String[]) null, (String) null, (String) null,
                            "_id LIMIT 50");
                    String devname = "null" ;
                    long _id  = 0  ;
                    String dev_nickName = "" ;
                    String dev_uid = "" ;
                    String view_acc = "" ;
                    String view_pwd  = "";
                    int event_notification  = 0  ;
                    int camera_channel  = 0 ;
                    byte[] var12  ;
                    int var13  = 0  ;
                    int ispublic  = 0  ;
                    while (var3.moveToNext()) {
                        _id = var3.getLong(0);
                        dev_nickName = var3.getString(1);
                        dev_uid = var3.getString(2);
                        view_acc = var3.getString(5);
                        view_pwd = var3.getString(6);
                        event_notification = var3.getInt(7);
                        camera_channel = var3.getInt(8);
                        var12 = var3.getBlob(9);
                        var13 = var3.getInt(10);
                        ispublic = var3.getInt(11);

                        int installmode = var3.getInt(12);
                        int hardware_pkg = var3.getInt(13);
                        if(dev_uid.equals(messuid))
                        {

                            deviceInfo = new DeviceInfo(_id, dev_uid, dev_nickName,
                                    dev_uid, dev_uid, view_pwd, "", event_notification, camera_channel, null);
                            // Log.i("IOTCamera", "DeviceInfo.size()>>>>>>>>>>>" + var16);
                            deviceInfo.installmode = installmode;
                            deviceInfo.connect_count = 0;
                            pushDeviceMap.put(messuid,event+"-"+timestamp);
                            devname =dev_nickName;
                            hasfoundUID= true;
                            break;
                        }
                    }
                    var3.close();
                    var2.close();

                    if(!hasfoundUID)
                    {
                        Log.v("deviceinfo", " 推送未能在本地找到相关设备 "  );
                        return;
                    }
                    if(deviceInfo==null){
                        Log.v("deviceinfo", "deviceInfo==null   UbiaApplication.messageUID =" + UbiaApplication.messageUID+"   messuid："+messuid+"  UbiaApplication.currentDeviceLive:"+UbiaApplication.currentDeviceLive);
                        return;
                    }
                    if(UbiaApplication.currentDeviceLive .equals(messuid)){
                        Log.v("deviceinfo", "Same UID   UbiaApplication.messageUID =" + UbiaApplication.messageUID+"   messuid："+messuid+"  UbiaApplication.currentDeviceLive:"+UbiaApplication.currentDeviceLive);
                        return;
                    }
                    NotificationManager NoManager = (NotificationManager) UbiaApplication
                            .getInstance().getApplicationContext()
                            .getSystemService(Context.NOTIFICATION_SERVICE);


                    UbiaApplication.messageUID = messuid;
                    Log.v("deviceinfo", "UbiaApplication.messageUID =" + UbiaApplication.messageUID+"   messuid："+messuid);
                    Bundle var6 = new Bundle();
                    var6.putString( "dev_uid",messuid);
                    var6.putString( "dev_uuid",messuid);
                    var6.putString( "dev_uuid_deal",messuid);
                    var6.putBoolean( "NotificationManager",true);
                    Intent IntentLiveViewGLviewActivity = new Intent();
                    IntentLiveViewGLviewActivity.putExtras(var6);
                    IntentLiveViewGLviewActivity.setClass(context , MainActivity.class);

                    PendingIntent Pintent = PendingIntent.getActivity(UbiaApplication .getInstance().getApplicationContext(), 110, IntentLiveViewGLviewActivity, PendingIntent.FLAG_UPDATE_CURRENT);
                    //获取时间系统时间
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(System.currentTimeMillis()));


                    SimpleDateFormat sDateFormat    =   new    SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
                    String    eventdate    =    sDateFormat.format(new    java.util.Date());

                    boolean  showactivity = false;
                    Resources res = context.getResources();
                    Bitmap nty_alert = BitmapFactory.decodeResource(res, R.drawable.nty_alert);
                    String title = UbiaApplication.getInstance().getString(R.string.page26_page34_MyPushMessageReceiver_alarm_alert_frombell) ;
                    if(  event.equals("push")){
                        title =UbiaApplication.getInstance(). getString(R.string.page26_page34_MyPushMessageReceiver_alarm_alert_frombell) ;
                    } else if(event.equals("plug")) {
                        title = UbiaApplication.getInstance(). getString(R.string.page26_page34_MyPushMessageReceiver_alarm_plug_frombell) ;
                    }
                    else{
                        title =UbiaApplication.getInstance(). getString(R.string.page26_page34_MyPushMessageReceiver_alarm_pir_frombell) ;
                    }
                    //获取时间系统时间


                        if (Build.VERSION.SDK_INT>=26){

                            String channelId = "channel_1";
                            String channelName = "push";

                            NotificationChannel channel = new NotificationChannel(channelId,
                                    channelName, NotificationManager.IMPORTANCE_DEFAULT);

                            NoManager.createNotificationChannel(channel);

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,channelId); //与channelId对应
                            builder .setSmallIcon (R.drawable.nty_alert)
                                    .setLargeIcon( nty_alert)
                                    .setContentIntent(Pintent)
                                    .setTicker(" "+title)
                                    .setContentText(""+title)
                                    .setContentTitle( " " + devname).setAutoCancel(true);

                            NoManager.notify(0, builder.build());

                            Log.d(TAG, "Receive onReceive message...8.0");

                        }else {


                            Notification.Builder builder = new Notification.Builder(UbiaApplication
                                    .getInstance().getApplicationContext())
                                    .setSmallIcon(R.drawable.nty_alert)
                                    .setLargeIcon(nty_alert)
                                    .setDefaults(Notification.DEFAULT_ALL)
                                    .setPriority(Notification.PRIORITY_HIGH)
//     	 .setFullScreenIntent(Pintent, true)
                                    .setContentIntent(Pintent)
                                    .setTicker(" " + title)
                                    .setContentText("" + title)
                                    .setContentTitle(" " + devname).setAutoCancel(true);
                            NoManager.notify(0, builder.build());

                            Log.d(TAG, "Receive onReceive message");
                        }



           //     }
            } catch ( Exception e) {
                Log.d(TAG, "?JSONException  Receive onReceive message");
                e.printStackTrace();
//  		}
            }
        }

        timeLastnotify = System.currentTimeMillis();

    }


}