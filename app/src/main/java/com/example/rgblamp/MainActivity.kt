package com.example.rgblamp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var btSocket: BluetoothSocket
    private lateinit var mConnectedThread: ConnectedThread
    private lateinit var h: Handler
    private lateinit var bitmap: Bitmap
    private val requestEnableBt = 1
    private val receiveMessage = 1
    private val tag = "bluetooth2"
    private val address = "00:21:13:03:39:C4"
    private val myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var red = 0xff
    private var green = 0xff
    private var blue = 0xff
    private var brightness = 255

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        checkBTState()

        color_picker.isDrawingCacheEnabled = true
        color_picker.buildDrawingCache(true)

        color_picker.setOnTouchListener { v, event ->
            if (event?.action == MotionEvent.ACTION_DOWN
                    || event?.action == MotionEvent.ACTION_MOVE) {
                bitmap = color_picker.drawingCache
                val pixel = bitmap.getPixel(event.x.toInt(), event.y.toInt())

                red = Color.red(pixel)
                green = Color.green(pixel)
                blue = Color.blue(pixel)

                displayColor()
                v?.performClick()
            }
            true
        }

        brightnessBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightness = progress
                displayColor()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {    }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {     }
        })
    }

    override fun onResume() {
        super.onResume()

        Log.d(tag, "...onResume - попытка соединения...")

        val device = btAdapter.getRemoteDevice(address)

        try {
            btSocket = device.createRfcommSocketToServiceRecord(myUUID)
        } catch (e: IOException) {
            errorExit("In onResume() and socket create failed: ${e.message}.")
        }

        Log.d(tag, "...Соединяемся...")
        try {
            btSocket.connect()
            Log.d(tag, "...Соединение установлено и готово к передаче данных...")
        } catch (e: IOException) {
            try {
                btSocket.close()
            } catch (e2: IOException) {
                errorExit("In onResume() and unable to close socket during connection failure: ${e2.message}.")
            }
        }
        Log.d(tag, "...Создание Socket...")

        mConnectedThread = ConnectedThread(btSocket)
        mConnectedThread.start()
    }

    override fun onPause() {
        super.onPause()

        Log.d(tag, "...In onPause()...")

        try {
            btSocket.close()
        } catch (e: IOException) {
            errorExit("In onPause() and failed to close socket. ${e.message}.")
        }
    }

    private fun displayColor() {
        val currentRed = red * brightness / 255
        val currentGreen = green * brightness / 255
        val currentBlue = blue * brightness / 255
        val currentColor = (currentGreen shl 16) + (currentRed shl 8) + currentBlue
        val hex = "#%06X".format(currentColor)

        color_view.setBackgroundColor(Color.rgb(currentRed, currentGreen, currentBlue))

        mConnectedThread.write(hex)
    }

    private fun checkBTState() {
        if (btAdapter.isEnabled) {
            Log.d(tag, "...Bluetooth включен...")
        } else {
            //Prompt user to turn on Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, requestEnableBt)
        }
    }

    private fun errorExit(message: String) {
        Toast.makeText(baseContext, "Fatal Error - $message", Toast.LENGTH_LONG).show()
        finish()
    }

    inner class ConnectedThread(mmSocket: BluetoothSocket): Thread() {
        private lateinit var mmInStream: InputStream
        private lateinit var mmOutStream: OutputStream

        init {
            try {
                mmInStream = mmSocket.inputStream
                mmOutStream = mmSocket.outputStream
            } catch (e: IOException) {}
        }

        override fun run() {
            val buffer = ByteArray(256)
            var bytes: Int

            while (true) {
                try {
                    bytes = mmInStream.read(buffer)
                    h.obtainMessage(receiveMessage, bytes, -1, buffer).sendToTarget()
                } catch (e: IOException) {
                    break
                }
            }
        }

        fun write(message: String) {
            Log.d(tag, "...Данные для отправки: $message...")
            val msgBuffer = message.toByteArray()
            try {
                mmOutStream.write(msgBuffer)
            } catch (e: IOException) {
                Log.d(tag, "...Ошибка отправки данных: ${e.message}...")
            }
        }
    }

    fun lightingOn(view: View?) {
        mConnectedThread.write("0ff")
    }
}