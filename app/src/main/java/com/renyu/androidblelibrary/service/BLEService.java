package com.renyu.androidblelibrary.service;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.renyu.androidblelibrary.bean.BLECommandModel;
import com.renyu.androidblelibrary.params.Params;
import com.renyu.blelibrary.bean.BLEDevice;
import com.renyu.blelibrary.impl.BLEConnectListener;
import com.renyu.blelibrary.impl.BLEOTAListener;
import com.renyu.blelibrary.impl.BLEResponseListener;
import com.renyu.blelibrary.impl.BLEStateChangeListener;
import com.renyu.blelibrary.params.CommonParams;
import com.renyu.blelibrary.utils.BLEFramework;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * Created by renyu on 2017/6/6.
 */

public class BLEService extends Service {

    BLEFramework bleFramework;

    private final static int PACKET_LENGTH = 20;
    private final static int PACKET_PAYLOAD = 17;

    //收到的数据列
    private static HashMap<String, LinkedList<byte[]>> receiverCommandMaps;

    @Override
    public void onCreate() {
        super.onCreate();

        receiverCommandMaps=new HashMap<>();

        bleFramework=BLEFramework.getBleFrameworkInstance();
        bleFramework.setParams(this.getApplicationContext(),
                Params.UUID_SERVICE_MILI,
                Params.UUID_SERVICE_OTASERVICE,
                Params.UUID_SERVICE_WRITE,
                Params.UUID_SERVICE_READ,
                Params.UUID_SERVICE_OTA,
                Params.UUID_DESCRIPTOR,
                Params.UUID_DESCRIPTOR_OTA);
        bleFramework.setBleConnectListener(new BLEConnectListener() {
            @Override
            public void getAllScanDevice(BLEDevice bleDevice) {
                Log.d("BLEService", bleDevice.getDevice().getName() + " " + bleDevice.getDevice().getAddress());
                if (bleDevice.getDevice().getName()!=null && bleDevice.getDevice().getName().startsWith("iite")) {
                    EventBus.getDefault().post(bleDevice);
                }
            }
        });
        bleFramework.setBleStateChangeListener(new BLEStateChangeListener() {
            @Override
            public void getCurrentState(int currentState) {
                Log.d("BLEService", "currentState:" + currentState);
            }
        });
        bleFramework.setBleResponseListener(new BLEResponseListener() {
            @Override
            public void getResponseValues(byte[] value) {
                putCommand(value);
            }
        });
        bleFramework.setBleotaListener(new BLEOTAListener() {
            @Override
            public void showProgress(int progress) {

            }
        });
        bleFramework.initBLE();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent!=null && intent.getStringExtra(CommonParams.COMMAND)!=null) {
            if (intent.getStringExtra(CommonParams.COMMAND).equals(CommonParams.SCAN)) {
                bleFramework.startScan();
            }
            if (intent.getStringExtra(CommonParams.COMMAND).equals(CommonParams.CONN)) {
                bleFramework.startConn((BluetoothDevice) intent.getParcelableExtra(CommonParams.DEVICE));
            }
            if (intent.getStringExtra(CommonParams.COMMAND).equals(CommonParams.WRITE)) {
                bleFramework.addCommand(intent.getByteArrayExtra(CommonParams.BYTECODE));
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 指令封装
     * @param command
     * @param params
     * @return
     */
    private static String getBLECommand(int command, HashMap<String, String> params) {
        try {
            JSONObject object=new JSONObject();
            object.put("command", command);
            JSONObject param=new JSONObject();
            if (params!=null && params.size()>0) {
                Iterator<Map.Entry<String, String>> iterator=params.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry=iterator.next();
                    try {
                        param.put(entry.getKey(), Integer.parseInt(entry.getValue()));
                    } catch (NumberFormatException e) {
                        param.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            else {
                param.put("NULL", "NULL");
            }
            object.put("param", param);
            Log.d("BLEService", object.toString());
            return object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static byte[][] getDivided_data(String json_str, int command) {
        byte[] bytes = json_str.getBytes();
        int total=bytes.length%PACKET_PAYLOAD==0?bytes.length/PACKET_PAYLOAD:bytes.length/PACKET_PAYLOAD+1;
        byte[][] divided_byte = new byte[total][];
        for (int i=0;i<total;i++) {
            if (total-1!=i) {
                divided_byte[i]=new byte[PACKET_LENGTH];
                for (int j=0;j<PACKET_LENGTH;j++) {
                    if (j==0) {
                        divided_byte[i][0]= (byte) (i+1);
                    }
                    else if (j==1) {
                        divided_byte[i][1]= (byte) total;
                    }
                    else if (j==2) {
                        divided_byte[i][2]= (byte) command;
                    }
                    else {
                        divided_byte[i][j]= bytes[(i*PACKET_PAYLOAD+(j-3))];
                    }
                }
            }
            else {
                divided_byte[i]=new byte[bytes.length-PACKET_PAYLOAD*(total-1)+3];
                for (int j=0;j<(bytes.length-PACKET_PAYLOAD*(total-1)+3);j++) {
                    if (j==0) {
                        divided_byte[i][0]= (byte) (i+1);
                    }
                    else if (j==1) {
                        divided_byte[i][1]= (byte) total;
                    }
                    else if (j==2) {
                        divided_byte[i][2]= (byte) command;
                    }
                    else {
                        divided_byte[i][j]= bytes[(i*PACKET_PAYLOAD+(j-3))];
                    }
                }
            }
        }
        return divided_byte;
    }

    private static String getOrigin_str(byte[][] byte_str) {
        String origin_str;
        int number=0;
        for (int i=0;i<byte_str.length;i++) {
            for (int j=3;j<byte_str[i].length;j++) {
                if (byte_str[i][j]!=0) {
                    number+=1;
                }
            }
        }
        byte[] bytes=new byte[number];
        int index=0;
        for (int i=0;i<byte_str.length;i++) {
            for (int j=3;j<byte_str[i].length;j++) {
                if (byte_str[i][j]!=0) {
                    bytes[index]=byte_str[i][j];
                    index++;
                }
            }
        }
        try {
            origin_str=new String(bytes, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            origin_str="";
        }
        return origin_str;
    }

    /**
     * 发送指令
     * @param command
     * @param params
     * @param context
     */
    public static void sendCommand(int command, HashMap<String, String> params, Context context) {
        String values=getBLECommand(command, params);
        byte[][] bytes_send = getDivided_data(values, command);
        for (byte[] bytes : bytes_send) {
            Intent intent=new Intent(context, BLEService.class);
            intent.putExtra(CommonParams.COMMAND, CommonParams.WRITE);
            intent.putExtra(CommonParams.BYTECODE, bytes);
            context.startService(intent);
        }
    }

    /**
     * 解析指令
     * @param bytes
     */
    private static synchronized void putCommand(byte[] bytes) {
        int command= com.renyu.blelibrary.utils.Utils.convert2To10(com.renyu.blelibrary.utils.Utils.sign2nosign(bytes[2]));
        //最后一条数据
        if (bytes[0]==bytes[1]) {
            //判断指令是否在集合中，如果不在则忽略本条指令
            if (receiverCommandMaps.containsKey(""+command)) {
                LinkedList<byte[]> list=receiverCommandMaps.get(""+command);
                list.add(bytes);
                //如果集合中数量跟指令条数一直，则进一步判断，否则则忽略本条指令
                if (list.size()==bytes[1]) {
                    byte[][] bytes1=new byte[list.size()][];
                    for (int i=0;i<bytes1.length;i++) {
                        bytes1[i]=list.get(i);
                    }
                    String result=getOrigin_str(bytes1);
                    Log.d("BLEService", result);
                    //符合json数据格式，则认为是有效指令，并将其转发
                    try {
                        // 测试指令是否正常
                        new JSONObject(result);

                        BLECommandModel model=new BLECommandModel();
                        model.setCommand(command);
                        model.setValue(result);
                        EventBus.getDefault().post(model);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.d("BLEService", "指令错误");
                    } finally {
                        //清除指令
                        receiverCommandMaps.remove(""+command);
                    }
                }
            }
        }
        else {
            LinkedList<byte[]> list;
            if (receiverCommandMaps.containsKey(""+command)) {
                list=receiverCommandMaps.get(""+command);
            }
            else {
                list=new LinkedList<>();
            }
            list.add(bytes);
            receiverCommandMaps.put(""+command, list);
        }
    }
}