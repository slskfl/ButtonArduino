package com.example.buttonarduino;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//아두이노에서 버튼 값 받기 (데이터 수신 부분만 활용)
//실행하자마자 블투 연경

public class MainActivity extends AppCompatActivity {

    ImageView imgBluetooth;
    Switch swState1, swState2, swState3;

    BluetoothAdapter btAdapter;
    int paireDeviceCount=0; //블루투스에 연결된 장치 수
    Set<BluetoothDevice> devices; //블루투스 장치
    BluetoothDevice remoteDevice; //내가 사용할 블루투스 장치
    BluetoothSocket bluetoothSocket; //블루투스 통신
    OutputStream outputStream=null;
    InputStream inputStream=null;
    Thread wokerThread=null;
    String strDelimiter="\n";
    char charDelimiter='\n';
    byte readBuffer[];
    int readBufferPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imgBluetooth=findViewById(R.id.imgBluetooth);
        /* 쓰지 않는 이유?
        swState1=findViewById(R.id.swState1);
        swState2=findViewById(R.id.swState2);
        swState3=findViewById(R.id.swState3);
        */
        checkBluetooth(); //시작하자마자 블루투스 연결
    }

    //스마트폰의 블루투스 지원 여부 검사
    void checkBluetooth(){
        btAdapter=BluetoothAdapter.getDefaultAdapter();
        if(btAdapter==null){
            showToast("블루투스를 지원하지 않는 장치입니다.");
        }else {
            //장치가 블루투스를 지원하는 경우
            if(!btAdapter.isEnabled()){
                Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, 10);
            } else{
                selectDevice(); //장치가 블루투스를 지원하는 경우
            }
        }
    }

    //페어링된 장치 목록 출력 및 선택
    void selectDevice(){
        devices=btAdapter.getBondedDevices(); //블루투스 장치 목록 가져옴
        paireDeviceCount=devices.size();
        if(paireDeviceCount==0){
            //연결된 장치가 없음
            showToast("페어링된 장치가 없습니다.");
        }else{
            //연결된 장치가 있을 경우
            AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("블루투스 장치 선택");
            List<String> listItems=new ArrayList<String>();
            for(BluetoothDevice device:devices){
                listItems.add((device.getName()));
            }
            listItems.add("취소");
            final CharSequence[] items=listItems.toArray(new CharSequence[listItems.size()]);
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(which==paireDeviceCount){
                        showToast("취소를 선택했습니다.");
                    }else{
                        connectToSelectedDevice(items[which].toString());
                    }
                }
            });
            builder.setCancelable(false); //뒤로가기 버튼 금지
            AlertDialog dlg=builder.create();
            dlg.show();
        }
    }

    //선택한 블루투스 장치와의 연결
    void connectToSelectedDevice(String selectedDeviceName){
        remoteDevice=getDeviceFormBoundedList(selectedDeviceName);
        UUID uuid=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //블루투스 장치 고유번호
        try{
            bluetoothSocket=remoteDevice.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();//기기와 연결이 완료
            imgBluetooth.setImageResource(R.drawable.bluetooth_icon);
            outputStream=bluetoothSocket.getOutputStream();
            inputStream=bluetoothSocket.getInputStream();
            beginListenForData();
        }catch (Exception e){
            showToast("소켓 연결이 되지 않았습니다.");
        }
    }

    //데이터 수신 준비 및 처리
    void beginListenForData(){
        final Handler handler=new Handler();
        readBuffer=new byte[1024]; //아두이노에서 받는 수신버퍼 크기
        readBufferPosition=0; //버퍼 내 수신 문자 저장 위치
        //문자열 수신 쓰레드
        wokerThread=new Thread(new Runnable() {
            @Override
            public void run() {
                while(!Thread.currentThread().isInterrupted()){ //쓰레드가 중단된 상태가 아닐 경우
                    try{
                        int byteAvailalbe=inputStream.available(); //수신데이터가 있는지 확인
                        if(byteAvailalbe>0){
                            //아두이노에서 보낸 데이터가 있음
                            byte[] packetBytes=new byte[byteAvailalbe];
                            inputStream.read(packetBytes);
                            for(int i=0; i<byteAvailalbe; i++){
                                byte b=packetBytes[i];
                                if(b==charDelimiter){ //마지막에 '\n'이 들어 올 경우
                                    byte[] encodeByte=new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodeByte, 0, encodeByte.length);
                                    final String data=new String(encodeByte, "US-ASCII");
                                    readBufferPosition=0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            //data변수에 수신된 문자열에 대한 처리 작업
                                            char btn_state[]=data.toCharArray();
                                            Switch s=null;
                                            switch (btn_state[0]){ //몇번째 버튼이 눌렸는지 확인
                                                case '1':
                                                    s=findViewById(R.id.swState1);
                                                    break;
                                                case '2':
                                                    s=findViewById(R.id.swState2);
                                                    break;
                                                case '3':
                                                    s=findViewById(R.id.swState3);
                                                    break;
                                            }
                                            if(s!=null){
                                                //블루투스와 연결되었는지 확인
                                                if(btn_state[1]=='0'){
                                                    s.setChecked(false);
                                                }
                                                if(btn_state[1]=='1'){
                                                    s.setChecked(true);
                                                }
                                            }
                                        }
                                    });
                                }else{
                                    readBuffer[readBufferPosition++]=b;
                                }
                            }
                        }
                    }catch (IOException e){
                        showToast("데이터 수신 중 오류가 발생했습니다.");
                    }
                }
            }
        });
        wokerThread.start();
    }
/* 현재 버튼 3개는 필요 없음
    //데이터 송신(아두이노로 전송)
    private void sendDate(String msg) {
        msg+=strDelimiter;
        try{
            outputStream.write(msg.getBytes()); //문자열 전송
        }catch (Exception e){
            showToast("문자");
        }
    }
*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            wokerThread.interrupt();
            inputStream.close();
            outputStream.close();
            bluetoothSocket.close();
        }catch (Exception e){
            showToast("앱 종료 중 에러 발생");
        }
    }

    //페어링된 블루투스 장치를 이름으로 찾기
    BluetoothDevice getDeviceFormBoundedList(String name){
        BluetoothDevice selectedDevice=null;
        for(BluetoothDevice device : devices){
            if(name.equals(device.getName())){ //대화상자에서 선태한 리스트 이름
                selectedDevice=device;
                break;
            }
        }
        return selectedDevice;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 10:
                if(resultCode==RESULT_OK){
                    selectDevice();
                } else if(resultCode==RESULT_CANCELED){
                    showToast("블루투스 활성화를 취소했습니다.");
                }
                break;
        }
    }

    void showToast(String str){
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }
}