package com.bistu.a3005.managesystem;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    GridView grid;
    TextView textView;
    List<Map<String, Object>> listItems;
    List<Map<String,String>> stationsConditionList = new ArrayList<Map<String,String>>();
    SimpleAdapter simpleAdapter;
    UDPBroadCast udpBroadCast= new UDPBroadCast();
    TCPCommunication tcpCommunication = new TCPCommunication();
    private String[] stations = new String[24];
    final static int RC_MSG_WHAT = 0x0001;
    final static String RC_MSG_KEY = "udpBroadCast";
    final android.os.Handler handler = new android.os.Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == RC_MSG_WHAT){

                textView.append("client report:\r\n");
                byte b[] = msg.getData().getByteArray(RC_MSG_KEY);
                String str = new String(b);
                //System.out.println(str);
                Pattern pattern = Pattern.compile("^(\\d{1,2}),\\d{1,2},([\\s\\w]+)");
                Matcher m = pattern.matcher(str);
                if(m.find()) {
                    //更新view
                    textView.append("   " + (str + "\r\n"));
                    int position = Integer.parseInt(m.group(1)) - 1;
                    Map<String, Object> item = listItems.get(position);
                    item.remove("image");
                    if (m.group(2).equals("normal"))//客户端汇报状态，normal：在线并正常工作，off line:离线，其他：在线但工作异常
                        item.put("image", R.drawable.gear_64px_g);
                    else if(m.group(2).equals("client off line"))//host 或client要离线
                        item.put("image", R.drawable.gear_64px_gr);
                    else
                        item.put("image",R.drawable.gear_64px_r);
                    simpleAdapter.notifyDataSetChanged();
                }
            }
            int offset=(textView.getLineCount()+1)*textView.getLineHeight();
            if(offset>textView.getHeight()){
                textView.scrollTo(0,offset-textView.getHeight());
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initUI();
        initUDPBroadCast();//使client获取server地址
        initTCPCommunication();//开始监听client的TCP连接
    }
    private void initView(){
        grid = (GridView) findViewById(R.id.gridView);
        textView = (TextView) findViewById(R.id.textView);
        textView.append("initView\r\n");
    }
    private void initUI(){
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
        textView.append("initUI start\r\n");
        for(int i=0;i<stations.length;i++){
            stations[i]=(i+1)+"号工作站";
        }
        listItems = new ArrayList<Map<String, Object>>();
        for(int i=0;i<stations.length;i++){
            Map<String, Object> listItem = new HashMap<String, Object>();
            listItem.put("station",stations[i]);
            listItem.put("image",R.drawable.gear_64px_gr);
            listItems.add(listItem);

            Map<String,String> stationCondition = new HashMap<String, String>();
            stationCondition.put("status","off line");
            stationCondition.put("cutNumber","0");
            stationCondition.put("IPAddress","none");
            stationsConditionList.add(stationCondition);
        }
        simpleAdapter = new SimpleAdapter(this,listItems,
                R.layout.cell,
                new String[]{"station","image"},
                new int[]{R.id.statusText,R.id.statusImage});

        grid.setAdapter(simpleAdapter);
        //添加列表项被单击的监听器
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Map<String, String> stationCondition = stationsConditionList.get(position);
                String str = "status:"+stationCondition.get("status");
                str +="\r\ncutNumber:";
                str +=stationCondition.get("cutNumber");
                str +="\r\nIPAddress:";
                str +=stationCondition.get("IPAddress");
                simpleAdapter.notifyDataSetChanged();
                showDialog(position+1+"号工作站",str);

            }
        });
        textView.append("initUI finish\r\n");
    }
    private void initUDPBroadCast(){
        textView.append("initUDPBroadCast start\r\n");
        textView.append("   start getLocalIPAddress\r\n");
        String localIpAddress = getLocalIpAddress();
        if(localIpAddress!=null) {
            textView.append("   has got local IP Address\r\n");
            udpBroadCast.setLocalIpAddress(localIpAddress);
            udpBroadCast.setPort(8005);
            try {
                udpBroadCast.receiver.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
            udpBroadCast.sender.setBroadCastAddress("255.255.255.255");
            udpBroadCast.sender.start();
            textView.append("   start broadcast host on line\r\n");
            new Thread(udpBroadCast.new BroadCast("host on line")).start();
        }else{
            textView.append("   IP address error\r\n");
            this.showDialog("网络错误！","请确认网络连接正确，并重启应用！");
        }
        textView.append("initUDPBroadCast finish\r\n");
    }
    private void initTCPCommunication(){
        tcpCommunication.setPort(8006);
        tcpCommunication.setUiHandler(handler);
        tcpCommunication.setMsgWhat(RC_MSG_WHAT);
        tcpCommunication.setMsgKey(RC_MSG_KEY);
        tcpCommunication.setStationsConditionList(stationsConditionList);
        tcpCommunication.start();
    }
    private void showDialog(String title, String msg){
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton("确定",null);
        builder.show();
    }
    private String getLocalIpAddress(){
        //获取wifi服务
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return intToIp(ipAddress);

    }
    private String intToIp(int i) {
        return (i & 0xFF ) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16 ) & 0xFF) + "." +
                ( i >> 24 & 0xFF) ;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(udpBroadCast.sender.isAlive()){
            new Thread(udpBroadCast.new BroadCast("host off line")).start();
            //System.out.println("host off line onPause");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //System.out.println("onResume");
        for(int i=0;i<stations.length;i++){
            Map<String, Object> listItem = listItems.get(i);
            if(listItem.get("image").equals(R.drawable.gear_64px_gr)) continue;
            listItem.remove("image");
            listItem.put("image",R.drawable.gear_64px_gr);

            Map<String,String> stationCondition = stationsConditionList.get(i);
            stationCondition.clear();
            stationCondition.put("status","off line");
            stationCondition.put("cutNumber","0");
            stationCondition.put("IPAddress","none");
        }
        simpleAdapter.notifyDataSetChanged();
        String localIpAddress = getLocalIpAddress();
        if(localIpAddress!=null){
            new Thread(udpBroadCast.new BroadCast("host on line")).start();
        }else{
            textView.append("   IP address error\r\n");
            this.showDialog("网络错误！","请确认网络连接正确，并重启应用！");
        }
    }
}
