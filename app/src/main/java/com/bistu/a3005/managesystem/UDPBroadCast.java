package com.bistu.a3005.managesystem;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.RunnableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 3005 on 2016/10/18.
 * 对于host和client 谁后上线谁发出广播，
 * host后上线向255.255.255.255发出4次host on line的广播，client回应 stationNum,cutNum,client received,host收到后提取client地址不再回应
 * client后上线向255.255.255.255发出4次1,0,normal广播，host提取ip地址并回应host received,client收到后提取host地址不再回应
 * 通讯阶段同client后上线类似，
 * client不断向host地址发出1，*，*广播，host回应host received，client收到后停止发送广播。
 * 离线状态下，分为host主动离线及client主动离线
 * host主动离线时,向255，255，255，255广播host off line，client回应1，2，client confirm;遍历stationsConditionList确认所有station都有回应。
 * client主动离线，向hostIP发送1,2,client off line，host回应host confirm,
 */
public   class UDPBroadCast{
    public Receiver receiver;
    public Sender sender;
    public HostOnLine hostOnLine;
    //public MsgProcess msgProcess;
    private String localIpAddress;
    private DatagramSocket ds=null;
    private int port;

    public UDPBroadCast(){
        receiver = new Receiver();
        sender = new Sender();
        hostOnLine = new HostOnLine();
        //msgProcess = new MsgProcess();
        //new Thread(msgProcess).start();
    }
    public void setPort(int port) {
        this.port = port;
        try {
            ds = new DatagramSocket(port);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void setLocalIpAddress(String localIpAddress) {
        this.localIpAddress = localIpAddress;
    }

    public class MsgProcess implements Runnable{
        public Handler handler;
        @Override
        public void run() {
            Looper.prepare();
            handler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if(msg.what==0x0002){
                        byte data[] = msg.getData().getByteArray("rawMsg");
                        String str = new String(data);
                        System.out.println(str);
                        Pattern pattern = Pattern.compile("^\\d{1,2},\\d{1,2},([\\w\\s]+),([\\w\\.]+)");
                        Matcher m = pattern.matcher(str);
                        if(m.find()){
                            if(m.group(1).equals("client on line")) {//收到client对host上线通知的应答
                                sender.setBroadCastAddress(m.group(2));
                                sender.send("ans for client on line");
                            }
                        }
                    }
                }
            };
            Looper.loop();
        }
    }
    public class HostOnLine implements Runnable {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            for(int i=4;i>0;i--) {
                try {
                    Thread.sleep(1000);// 线程暂停1秒，单位毫秒
                    sender.send("host on line");
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    public class Receiver extends Thread{
        public void run() {
            byte buf[] = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            while (true) {
                try {
                    ds.receive(dp);
                    if(dp.getAddress().getHostAddress().equals(localIpAddress)) {//don't process msg broadcast by self
                       continue;
                    }
                    String str = new String(dp.getData(),0,dp.getLength());
                    System.out.println(str);
                    if(str.equals("client on line")){
                        sender.setBroadCastAddress(dp.getAddress().getHostAddress());
                        sender.send("ans for client on line");
                    }
                    //System.out.println("client ip:" + new String(temp));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public class Sender extends Thread{
        private InetAddress broadCastAddress;
        private android.os.Handler mHandler;
        String str;
        public void run(){
            Looper.prepare();
            mHandler= new android.os.Handler(){
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what==0x1234){
                        byte data[] = str.getBytes();
                        try{
                            DatagramPacket dp = new DatagramPacket(data,data.length,broadCastAddress,port);
                            ds.send(dp);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            };
            Looper.loop();
        }
        public void send(String str){
            this.str= str;
            while(mHandler==null);
            mHandler.sendEmptyMessage(0x1234);
        }
        public void setBroadCastAddress(String broadCastAddress) {
            try {
                this.broadCastAddress = InetAddress.getByName(broadCastAddress);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }


}
