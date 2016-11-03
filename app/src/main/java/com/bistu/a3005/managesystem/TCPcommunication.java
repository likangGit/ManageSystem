package com.bistu.a3005.managesystem;

import android.os.Bundle;
import android.os.Message;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 3005 on 2016/10/30.
 */
public class TCPCommunication extends Thread {
    ServerSocket serverSocket;
    int port;
    private android.os.Handler uiHandler=null;
    private int MSG_WHAT;
    private String MSG_KEY;
    List<Map<String,String>> stationsConditionList;
    //List<Socket> sockets = new ArrayList<Socket>();
    public void setPort(int port) {
        this.port = port;
        try{
            serverSocket = new ServerSocket(port);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void setUiHandler(android.os.Handler handler) {
        this.uiHandler = handler;
    }
    public void setMsgWhat(int msgWhat) {
        this.MSG_WHAT = msgWhat;
    }
    public void setMsgKey(String msg_key){
        this.MSG_KEY = msg_key;
    }
    public void setStationsConditionList(List<Map<String, String>> stationsConditionList) {
        this.stationsConditionList = stationsConditionList;
    }
    @Override
    public void run() {
        super.run();
        while (true) try {
            // 调用ServerSocket的accept()方法，接受客户端所发送的请求，
            // 如果客户端没有发送数据，那么该线程就停滞不继续
            Socket socket = serverSocket.accept();
            // 从Socket当中得到InputStream对象
            InputStream inputStream = socket.getInputStream();
            byte buffer[] = new byte[1024 * 4];
            int temp = inputStream.read(buffer);
            if (temp <= 0) continue;
            System.out.println(temp);
            String str = new String(buffer, 0, temp);
            System.out.println(str);
            Pattern pattern = Pattern.compile("^(\\d{1,2}),(\\d{1,2}),([\\w\\s]+)");
            Matcher m = pattern.matcher(str);
            if (m.find()) {
                int position = Integer.parseInt(m.group(1)) - 1;
                String IPAddress = socket.getInetAddress().getHostAddress();
                //通知ui更改view
                str = str + "," + IPAddress;
                Bundle bundle = new Bundle();
                bundle.putByteArray(MSG_KEY, str.getBytes());
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = MSG_WHAT;
                uiHandler.sendMessage(msg);

                Map<String, String> stationCondition = stationsConditionList.get(position);
                stationCondition.clear();
                if (m.group(3).equals("client off line")) {
                    stationCondition.put("cutNumber", "0");
                    stationCondition.put("status", m.group(3));
                    stationCondition.put("IPAddress", "none");
                } else {
                    stationCondition.put("cutNumber", m.group(2));
                    stationCondition.put("status", m.group(3));
                    stationCondition.put("IPAddress", IPAddress);
                }
            }
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        serverSocket.close();
    }
}
