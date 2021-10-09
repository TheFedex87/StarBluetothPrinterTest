package it.thefedex87.bluetoothprinttest

import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.ui.ScanningActivity
import com.starmicronics.stario.StarIOPort
import com.starmicronics.starioextension.ICommandBuilder
import com.starmicronics.starioextension.StarIoExt
import kotlinx.coroutines.delay
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {
    private lateinit var selectImageResultLauncher: ActivityResultLauncher<Intent?>

    private val btDeviceList = mutableListOf<BluetoothDevice>()

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothScanner: BluetoothLeScanner

    private lateinit var bluetoothDevice: BluetoothDevice

    private val btLeScanCallback: ScanCallback =
    object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result != null) {
                btDeviceList.add(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    private val bluetoothGattConnect: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(
                "BT_TEST",
                "BT connection response: $status - $newState - ${gatt?.device}"
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BT_TEST", "Connected, bond state: ${gatt!!.device.bondState}")

                    if (gatt.device.bondState == BOND_NONE) {
                        gatt.device.createBond()
                    }
                }
            } else if (status == 19) {
                gatt!!.disconnect()
                gatt.close()
            } else {
                gatt!!.disconnect()
                gatt.close()
            }
        }
    }

    private val bondBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("BT_TEST", "$intent")
            Log.d("BT_TEST", "Extras: ${intent?.extras}")
            Log.d(
                "BT_TEST",
                "EXTRA_BOND_STATE: ${intent?.extras?.get(EXTRA_BOND_STATE)}, PREV EXTRA_BOND_STATE: ${
                    intent?.extras?.get(
                        EXTRA_PREVIOUS_BOND_STATE
                    )
                }"
            )
            when (intent?.extras?.get(EXTRA_BOND_STATE)) {
                BOND_NONE -> {

                }
                BOND_BONDING -> {

                }
                BOND_BONDED -> {
                    bluetoothDevice.connectGatt(
                        this@MainActivity,
                        false,
                        bluetoothGattConnect,
                        BluetoothDevice.TRANSPORT_LE
                    )
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(bondBroadcastReceiver, IntentFilter(ACTION_BOND_STATE_CHANGED))

        /*Printooth.init(this)
        selectImageResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

            }
        selectImageResultLauncher.launch(Intent(this, ScanningActivity::class.java))*/

        bluetoothManager = ContextCompat.getSystemService(this, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothScanner = bluetoothAdapter!!.bluetoothLeScanner



        val buttonScan = findViewById<Button>(R.id.button_scan)
        buttonScan.setOnClickListener {
            val bondedDevices = bluetoothAdapter!!.bondedDevices
            if (bondedDevices.any { it.name?.contains("STAR") ?: false }) {
                Log.d("BT_TEST", "Device bounded")
                val printer = bondedDevices.first { it.name?.contains("STAR") ?: false }
                printer.connectGatt(
                    this@MainActivity,
                    false,
                    bluetoothGattConnect,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                Log.d("BT_TEST", "Device not bounded")
                bluetoothScanner.startScan(btLeScanCallback)
                lifecycleScope.launchWhenStarted {
                    delay(5000)
                    bluetoothScanner.stopScan(btLeScanCallback)

                    Log.d("BT_TEST", btDeviceList.map { it.name }.toString())
                    val printer = btDeviceList.firstOrNull { it.name?.contains("STAR") ?: false }
                    printer?.let {
                        Log.d("BT_TEST", "Bond state: ${it.bondState}")
                        bluetoothDevice = it
                        //if (it.bondState == BOND_NONE) {
                        it.createBond()
                        //}
                        //it.connectGatt(this@MainActivity, false, bluetoothGattConnect, BluetoothDevice.TRANSPORT_LE)
                    }
                }
            }
        }

        val button = findViewById<Button>(R.id.button_test)
        button.setOnClickListener {
            /*val bpc = BluetoothPrintersConnections()
            val printer = EscPosPrinter(BluetoothPrintersConnections.selectFirstPaired(), 203, 48f, 32)
            printer.printFormattedText("[L]Prova" +
                    "[L]\n" +
                    "[C]Stampa")*/

            //selectImageResultLauncher.launch(Intent(this, ScanningActivity::class.java))

            val encoding = Charset.forName("UTF-8")
            val returnValue = StarIOPort.searchPrinter("BT:")
            if (returnValue.size > 0) {
                Snackbar.make(findViewById(R.id.container), "Printers found", Snackbar.LENGTH_LONG)
                    .show()
                val builder = StarIoExt.createCommandBuilder(StarIoExt.Emulation.StarPRNT)
                builder.beginDocument()
                builder.appendCodePage(ICommandBuilder.CodePageType.UTF8)
                builder.appendAlignment(ICommandBuilder.AlignmentPosition.Center)
                builder.append(("Print test\nBoh\n").toByteArray(encoding))

                builder.appendBarcode(
                    ("{B123456.").toByteArray(Charset.forName("US-ASCII")),
                    ICommandBuilder.BarcodeSymbology.Code128,
                    ICommandBuilder.BarcodeWidth.Mode2,
                    40,
                    true
                )

                builder.appendMultiple(("Prova\n").toByteArray(encoding), 2, 1)

                builder.appendEmphasis(("Altra prova\n").toByteArray(encoding))

                builder.appendInvert("Refunds and Exchanges\n".toByteArray(encoding))

                builder.appendCutPaper(ICommandBuilder.CutPaperAction.PartialCutWithFeed)
                builder.endDocument()


                val port = StarIOPort.getPort(returnValue[0].portName, "SM-L200", 10000)
                port.beginCheckedBlock()
                val commands = builder.commands
                port.writePort(commands, 0, commands.size)
                port.endCheckedBlock()

                StarIOPort.releasePort(port)
            } else {
                Snackbar.make(
                    findViewById(R.id.container),
                    "No printers found",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(bondBroadcastReceiver)
    }
}