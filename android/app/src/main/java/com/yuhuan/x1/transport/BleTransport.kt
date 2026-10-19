@file:Suppress("MissingPermission", "DEPRECATION")
package com.yuhuan.x1.transport

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.yuhuan.x1.core.Frames
import com.yuhuan.x1.core.Segment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

data class FoundDevice(val address: String, val name: String, val rssi: Int)
class BleTransport(private val context: Context, private val scope: CoroutineScope) : Transport {
    companion object {
        val SERVICE: UUID = UUID.fromString("8d4a0001-5c61-4d3d-a34b-e271e6fa1001")
        val CONTROL: UUID = UUID.fromString("8d4a0002-5c61-4d3d-a34b-e271e6fa1001")
        val DATA: UUID = UUID.fromString("8d4a0003-5c61-4d3d-a34b-e271e6fa1001")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    override val label = "蓝牙"
    val found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    val ready = MutableStateFlow(false)
    val state = MutableStateFlow("未连接")
    val address = MutableStateFlow("")
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var control: BluetoothGattCharacteristic? = null
    private var events = Channel<Pair<String,Int>>(Channel.UNLIMITED)
    private var packets = Channel<ByteArray>(Channel.UNLIMITED)
    private val lock = Mutex()
    private var mtu = 23
    private var request = 0
    private var scanJob: Job? = null
    private var scanner: BluetoothLeScanner? = null
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val item = FoundDevice(result.device.address,result.scanRecord?.deviceName ?: "语环 X1",result.rssi)
            found.value = (found.value.filterNot { it.address==item.address } + item).sortedByDescending { it.rssi }
        }
        override fun onScanFailed(errorCode: Int) { stopScan(); state.value="搜索失败（$errorCode），请重试" }
    }
    fun scan() {
        check(adapter?.isEnabled==true) { "请先打开手机蓝牙" }
        stopScan(); found.value=emptyList(); scanning.value=true
        scanner=adapter?.bluetoothLeScanner
        scanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
        scanJob=scope.launch { delay(15_000);stopScan() }
    }
    fun stopScan() { runCatching { scanner?.stopScan(scanCallback) };scanning.value=false;scanJob?.cancel();scanner=null }
    private val callback = object : BluetoothGattCallback() {
        private fun active(g: BluetoothGatt) = g===gatt
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (!active(g)) return
            if (status==BluetoothGatt.GATT_SUCCESS && newState==BluetoothProfile.STATE_CONNECTED)
                events.trySend("connected" to status)
            else if (newState==BluetoothProfile.STATE_DISCONNECTED || status!=0) {
                ready.value=false;state.value="连接已断开"
                val e=IllegalStateException("蓝牙连接中断（$status）")
                events.close(e);packets.close(e)
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, value: Int, status: Int) { if(active(g)) { if(status==0)mtu=value.coerceIn(23,247);events.trySend("mtu" to status) } }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { if(active(g))events.trySend("services" to status) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { if(active(g))events.trySend("descriptor" to status) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { if(active(g))events.trySend("write" to status) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { if(active(g)&&c.uuid==DATA)packets.trySend(value.copyOf()) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if(Build.VERSION.SDK_INT<33 && active(g)&&c.uuid==DATA)packets.trySend(c.value.copyOf()) }
    }
    private suspend fun await(type: String, timeout: Long = 30_000) = withTimeout(timeout) {
        while(true) { val e=events.receive(); if(e.first==type) { check(e.second==0) { "蓝牙操作失败：${e.second}" };break } }
    }
    suspend fun connect(target: String) = lock.withLock {
        check(BluetoothAdapter.checkBluetoothAddress(target))
        check(adapter?.isEnabled==true) { "请先打开手机蓝牙" }
        disconnect();stopScan();state.value="正在连接";events=Channel(Channel.UNLIMITED);packets=Channel(Channel.UNLIMITED);mtu=23
        try {
            val device=adapter!!.getRemoteDevice(target)
            gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
            await("connected")
            state.value="请确认系统配对提示"
            if(device.bondState!=BluetoothDevice.BOND_BONDED) {
                if(device.bondState==BluetoothDevice.BOND_NONE)check(device.createBond()) { "无法发起配对" }
                withTimeout(90_000) { while(device.bondState!=BluetoothDevice.BOND_BONDED) { delay(250);check(gatt!=null) } }
            }
            val g=gatt!!
            check(g.requestMtu(247));await("mtu")
            check(g.discoverServices());await("services")
            val service=g.getService(SERVICE) ?: error("不是语环 X1 设备")
            control=service.getCharacteristic(CONTROL) ?: error("控制服务不可用")
            val data=service.getCharacteristic(DATA) ?: error("数据服务不可用")
            check(g.setCharacteristicNotification(data,true))
            val descriptor=data.getDescriptor(CCCD) ?: error("设备不支持通知")
            if(Build.VERSION.SDK_INT>=33)check(g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS)
            else {descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;check(g.writeDescriptor(descriptor))}
            await("descriptor");address.value=target;ready.value=true;state.value="已连接"
        } catch(t: Throwable) { disconnect();state.value=if(t is TimeoutCancellationException)"连接超时，请重试" else t.message ?: "连接失败";throw t }
    }
    fun disconnect() {
        ready.value=false;control=null
        val old=gatt;gatt=null
        runCatching { old?.disconnect();old?.close() }
        events.close();packets.close();state.value="未连接"
    }
    private suspend fun request(body: JSONObject): Pair<Int,ByteArray> = lock.withLock {
        check(ready.value) { "请先连接语环 X1" }
        try {
            withTimeout(45_000) {
                request=request%65535+1
                val obj=JSONObject(body.toString()).put("req",request)
                val bytes=(obj.toString()+"\n").toByteArray(Charsets.UTF_8)
                require(bytes.size<=513) { "操作内容过长" }
                val frames=Frames(request,obj.optLong("offset",0))
                val g=gatt!!;val c=control!!
                for(part in bytes.asList().chunked(mtu-3)) {
                    val data=part.toByteArray()
                    if(Build.VERSION.SDK_INT>=33)check(g.writeCharacteristic(c,data,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS)
                    else { c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;c.value=data;check(g.writeCharacteristic(c)) }
                    await("write",15_000)
                }
                while(!frames.done)frames.append(packets.receive())
                val result=frames.bytes()
                if(frames.kind==1)checked(JSONObject(result.toString(Charsets.UTF_8)))
                check(!frames.error) { "设备读取失败" }
                frames.kind to result
            }
        } catch(t: Throwable) {
            // A cancelled write/CRC failure cannot safely share this GATT queue with a new request.
            if(t is CancellationException || t is IllegalArgumentException)disconnect()
            throw t
        }
    }
    override suspend fun command(body: JSONObject): JSONObject {
        val (kind,bytes)=request(body);check(kind==1) { "设备返回了错误的数据类型" }
        return checked(JSONObject(bytes.toString(Charsets.UTF_8)))
    }
    override suspend fun download(segment: Segment, partial: File, progress: (Long)->Unit) {
        RandomAccessFile(partial,"rw").use { out ->
            if(out.length()>segment.bytes)out.setLength(0)
            var offset=out.length();out.seek(offset);progress(offset)
            while(offset<segment.bytes) {
                currentCoroutineContext().ensureActive()
                val (kind,data)=request(command("read","id" to segment.id,"offset" to offset,"length" to minOf(4096,segment.bytes-offset)))
                check(kind==2 && data.isNotEmpty() && data.size<=segment.bytes-offset) { "设备音频数据不完整" }
                out.write(data);out.fd.sync();offset+=data.size;progress(offset)
            }
        }
    }
}
