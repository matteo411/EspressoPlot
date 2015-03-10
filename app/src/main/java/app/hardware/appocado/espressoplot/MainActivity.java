package app.hardware.appocado.espressoplot;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.LineAndPointRenderer;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.PointLabeler;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity {

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    private XYPlot pressure_plot;
    private XYPlot rpm_plot;
    private XYPlot temperature_plot;

    private SimpleXYSeries series_Pressure;
    private SimpleXYSeries series_RPM;
    private SimpleXYSeries series_Temperature;

    private TextView tempTextBox;
    private TextView pressureTextBox;
    private TextView motorTextBox;

    private SeekBar motorControlSeekBar;

    private float current_pressure;
    private int current_motor;
    private float current_temperature;

    private static int DAC_bit_max = 4095; // max for 12bit DAC = 2^12 - 1
    private static int max_samples_to_plot = 200;
    private static int label_frequency = 10;
    private static int domain_boundary_upper = 200;

    private double mStartTime = 0L;

    private Thread timer;
    private TextView shotTimerTextBox;

    @Override
    public void onStart() {
        super.onStart();

        //plot_dummy_data();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // remove title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);


        try {
            findBT();
            openBT();
        } catch (IOException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        } // just do it
        


        /*
		motorControlSeekBar = (SeekBar) findViewById(R.id.seekBar1);
		motorControlSeekBar
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {

					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {

					}

					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {

						int DACvalue = (DAC_bit_max * progress) / 100;

						//tv_slider.setText("12 bit DAC motor control value: "
						//		+ Integer.toString(DACvalue));

						String s_DACvalue = "<M>" + Integer.toString(DACvalue) + ",";

						writeStringToBlueTooth(s_DACvalue);

					}
				});
        */

        tempTextBox = (TextView) findViewById(R.id.textViewTemp);
        motorTextBox = (TextView) findViewById(R.id.textViewMotor);
        pressureTextBox = (TextView) findViewById(R.id.textViewPressure);

        shotTimerTextBox = (TextView) findViewById(R.id.shotTimerText);

        // initialize our XYPlot references
        pressure_plot = (XYPlot) findViewById(R.id.xyplot_pressure);
        rpm_plot = (XYPlot) findViewById(R.id.xyplot_rpm);
        temperature_plot = (XYPlot) findViewById(R.id.xyplot_temperature);

        series_Pressure = new SimpleXYSeries(null,
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "transducer samples");

        series_RPM = new SimpleXYSeries(null,
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "tach out samples");

        series_Temperature = new SimpleXYSeries(null,
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "temperature samples");


        LineAndPointFormatter motorLinePointFormatter = new LineAndPointFormatter();
        motorLinePointFormatter.setPointLabelFormatter(new PointLabelFormatter());
        motorLinePointFormatter.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);

        motorLinePointFormatter.setPointLabeler(new PointLabeler() {
            @Override
            public String getLabel(XYSeries xySeries, int i) {
                return i % label_frequency == 0 ? xySeries.getY(i).toString() : "";
            }
        });


        LineAndPointFormatter pressureLinePointFormatter = new LineAndPointFormatter();
        pressureLinePointFormatter.setPointLabelFormatter(new PointLabelFormatter());
        pressureLinePointFormatter.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);

        pressureLinePointFormatter.setPointLabeler(new PointLabeler() {
            @Override
            public String getLabel(XYSeries xySeries, int i) {
                return i % label_frequency == 0 ? xySeries.getY(i).toString() : "";
            }
        });

        LineAndPointFormatter tempLinePointFormatter = new LineAndPointFormatter();
        tempLinePointFormatter.setPointLabelFormatter(new PointLabelFormatter(Color.WHITE));
        tempLinePointFormatter.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf3);

        tempLinePointFormatter.setPointLabeler(new PointLabeler() {
            @Override
            public String getLabel(XYSeries xySeries, int i) {
                return i % label_frequency == 0 ? xySeries.getY(i).toString() : "";
            }
        });

        series_RPM.useImplicitXVals();
        rpm_plot.addSeries(series_RPM, motorLinePointFormatter);
        //rpm_plot.setTicksPerRangeLabel(1);
        //rpm_plot.setTicksPerDomainLabel(1);
        XYGraphWidget rpmWidget = rpm_plot.getGraphWidget();
        rpmWidget.setDomainLabelOrientation(-45);

        //rpmWidget.setRangeLabelHorizontalOffset(-60);
        rpm_plot.getGraphWidget().setDomainLabelOrientation(-45);
        rpm_plot.setDomainValueFormat(new DecimalFormat("#"));
        rpm_plot.setDomainBoundaries(1, domain_boundary_upper, BoundaryMode.FIXED);
        rpm_plot.setRangeValueFormat(new DecimalFormat("#"));
        rpm_plot.setRangeBoundaries(0, 1200, BoundaryMode.FIXED);
        rpm_plot.getLegendWidget().setVisible(false);

        //RPM is the base layer plot so we don't set everything to alpha 0 like the others
        //rpmWidget.getBackgroundPaint().setAlpha(0);
        //rpmWidget.getDomainLabelPaint().setAlpha(0);
        rpmWidget.getRangeLabelPaint().setColor(getResources().getColor(R.color.motor_line_color));
        rpm_plot.getRangeLabelWidget().setVisible(false); //remove the bar label

        //rpmWidget.getGridBackgroundPaint().setAlpha(0);
        //rpmWidget.getDomainOriginLinePaint().setAlpha(0);
        rpmWidget.getRangeOriginLabelPaint().setAlpha(0);
        //rpmWidget.getDomainGridLinePaint().setAlpha(0);

        Paint graphFill = new Paint();
        graphFill.setAlpha(200);
        LinearGradient lg = new LinearGradient(0, 0, 0, 250,
                getResources().getColor(R.color.back_gradient_2),
                getResources().getColor(R.color.back_gradient_1),
                Shader.TileMode.MIRROR);
        graphFill.setShader(lg);

        rpmWidget.getGridBackgroundPaint().set(graphFill);

        series_Pressure.useImplicitXVals();
        pressure_plot.addSeries(series_Pressure, pressureLinePointFormatter);
        //pressure_plot.setTicksPerRangeLabel(20);
        //pressure_plot.setTicksPerDomainLabel(2);
        XYGraphWidget pressureWidget = pressure_plot.getGraphWidget();
        pressureWidget.setDomainLabelOrientation(-45);
        pressure_plot.setDomainValueFormat(new DecimalFormat("#"));
        pressure_plot.setDomainBoundaries(1, domain_boundary_upper, BoundaryMode.FIXED);
        pressure_plot.setRangeValueFormat(new DecimalFormat("#"));
        pressure_plot.setRangeBoundaries(1, 15, BoundaryMode.FIXED);
        pressure_plot.getLegendWidget().setVisible(false);

        //set transparency values
        pressure_plot.getBorderPaint().setAlpha(0);
        pressure_plot.getBackgroundPaint().setAlpha(0);
        pressureWidget.getBackgroundPaint().setAlpha(0);
        pressureWidget.getDomainLabelPaint().setAlpha(0);
        pressureWidget.getRangeLabelPaint().setColor(getResources().getColor(R.color.pressure_line_color));
        pressure_plot.getRangeLabelWidget().setVisible(false); //remove the bar label
        pressureWidget.getGridBackgroundPaint().setAlpha(0);
        pressureWidget.getDomainOriginLinePaint().setAlpha(0);
        pressureWidget.getRangeOriginLabelPaint().setAlpha(0);
        pressureWidget.getDomainGridLinePaint().setAlpha(0);

        series_Temperature.useImplicitXVals();
        temperature_plot.addSeries(series_Temperature, tempLinePointFormatter);
        //temperature_plot.setTicksPerRangeLabel(1);
        //temperature_plot.setTicksPerDomainLabel(1);
        XYGraphWidget tempWidget = temperature_plot.getGraphWidget();
        tempWidget.setDomainLabelOrientation(-45);
        temperature_plot.setDomainValueFormat(new DecimalFormat("#"));
        temperature_plot.setDomainBoundaries(1, domain_boundary_upper, BoundaryMode.FIXED);
        temperature_plot.setRangeValueFormat(new DecimalFormat("#"));
        temperature_plot.setRangeBoundaries(160, 240, BoundaryMode.FIXED);
        temperature_plot.getLegendWidget().setVisible(false);

        //set transparency values
        temperature_plot.getBorderPaint().setAlpha(0);
        temperature_plot.getBackgroundPaint().setAlpha(0);
        tempWidget.getBackgroundPaint().setAlpha(0);
        tempWidget.getDomainLabelPaint().setAlpha(0);
        tempWidget.getRangeLabelPaint().setColor(getResources().getColor(R.color.temperature_line_color));
        temperature_plot.getRangeLabelWidget().setVisible(false); //remove the Fahrenheit label
        tempWidget.getGridBackgroundPaint().setAlpha(0);
        tempWidget.getDomainOriginLinePaint().setAlpha(0);
        tempWidget.getRangeOriginLabelPaint().setAlpha(0);
        tempWidget.getDomainGridLinePaint().setAlpha(0);

        //temperature_plot.getRangeLabelWidget().setVisible(false);
        //rpm_plot.getRangeLabelWidget().setVisible(false);
        //pressure_plot.getRangeLabelWidget().setVisible(false);

        rpmWidget.getRangeLabelPaint().setAlpha(0);
        pressureWidget.getRangeLabelPaint().setAlpha(0);
        tempWidget.getRangeLabelPaint().setAlpha(0);
        //AXIS positionings
        //rpmWidget.setRangeAxisPosition(false, false, 10, "10");
        //rpmWidget.setRangeLabelHorizontalOffset(80);
        //rpmWidget.setRangeLabelVerticalOffset(10);

        //pressureWidget.setRangeLabelHorizontalOffset(50);
        //pressureWidget.setRangeLabelVerticalOffset(10);

        //tempWidget.setRangeLabelHorizontalOffset(-40);
        //tempWidget.setRangeLabelVerticalOffset(10);

        int rightMargin = 90;

        //rpmWidget.setMarginRight(rightMargin);
        //tempWidget.setMarginRight(rightMargin);
        //pressureWidget.setMarginRight(rightMargin);

        //set offset
        //tempWidget.setRangeAxisPosition(false,false,0,"0");

        //tempWidget.setRangeLabelVerticalOffset(-5);

    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //if (mBluetoothAdapter == null) {
        //	myLabel.setText("No bluetooth adapter available");
        //}

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
                .getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("mExpobar")) {
                    mmDevice = device;
                    break;
                }
            }
        }
        //myLabel.setText("Bluetooth Device Found");
    }

    void openBT() throws IOException {

        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // Standard
        // SerialPortService
        // ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);

        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            mmSocket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and get out
            try {
                mmSocket.close();
            } catch (IOException closeException) { }
            return;
        }


        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        writeStringToBlueTooth("<R>,"); // tell arduino to reset
        beginListenForData();
        writeStringToBlueTooth("<L>,"); // tell arduino to start logging


    }

    void writeStringToBlueTooth(String str) {
        byte[] bytes = null;
        try {
            bytes = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try {
            mmOutputStream.write(bytes);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    void plot_dummy_data() {
        for (int i = 0; i < 200; i++) {
            series_Pressure.addLast(null, i*0.1);
            pressure_plot.redraw();
            series_RPM.addLast(null, 300+ i);
            rpm_plot.redraw();
            series_Temperature.addLast(null,170+i*0.4);
            temperature_plot.redraw();
        }

        //pressure_plot.redraw();
        //rpm_plot.redraw();
        //temperature_plot.redraw();
    }

    int item_count = 0;
    int motorSetting = 0;
    float pressure = 0;
    float temp = 0;

    void decisionMakerIncomingString(String totalStr) {

        String commandStr = totalStr.substring(0,3);
        String dataStr = totalStr.substring(3, totalStr.length());

        if (commandStr.equals("[P]")) {
            pressure = Float.parseFloat(dataStr);
            item_count++;
        } else if (commandStr.equals("[M]")) {
            motorSetting = Integer.parseInt(dataStr);
            item_count++;
        } else if (commandStr.equals("[T]")) {
            temp = Float.parseFloat(dataStr);
            item_count++;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pressureTextBox.setText(String.valueOf(pressure));
                motorTextBox.setText(String.valueOf(motorSetting));
                tempTextBox.setText(String.valueOf(temp));

                if (motorSetting != 0) {
                    long millis = SystemClock.uptimeMillis() - (long) mStartTime;
                    float seconds = (millis / (float) 1000);
                    shotTimerTextBox.setText(String.valueOf(seconds));
                }
            }
        });


        if (item_count % 3 == 0) {

            if (motorSetting == 0) {
                mStartTime = System.currentTimeMillis();

                while(series_Pressure.size() > 0) {
                    series_Pressure.removeLast();
                    series_RPM.removeLast();
                    series_Temperature.removeLast();
                }
            }
            else {

                series_Pressure.addLast(null, pressure);
                series_RPM.addLast(null, motorSetting);
                series_Temperature.addLast(null, temp);

                if (series_Pressure.size() > max_samples_to_plot) {
                    series_Pressure.removeFirst();
                    series_RPM.removeFirst();
                    series_Temperature.removeFirst();
                }

                pressure_plot.redraw();
                rpm_plot.redraw();
                temperature_plot.redraw();
            }
        }

    }

    void beginListenForData() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final byte delimiter = 44; // 44 = "," //10 = ASCII code for a newline
        // character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];

                            mmInputStream.read(packetBytes);

                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];

                                if (b == delimiter) {
                                    readBuffer[readBufferPosition++] = b;

                                    byte[] encodedBytes = new byte[readBufferPosition];

                                    System.arraycopy(readBuffer, 0,
                                            encodedBytes, 0,
                                            encodedBytes.length); // minus 1 to
                                    // leave out
                                    // the
                                    // delimeter
                                    final String stringData = new String(
                                            encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    // pass in the data string without the comma
                                    decisionMakerIncomingString(stringData
                                            .substring(0,
                                                    stringData.length() - 1));

                                } else // keep filling buffer until we reach the
                                // delimeter
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }

                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void closeBT() throws IOException {
        stopWorker = true;
        //workerThread.stop();
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
    }




    @Override
    public void onDestroy() {

        writeStringToBlueTooth("<R>,"); // tell arduino to reset

        try {
            closeBT();
        } catch (IOException ex) {
            String msg = ex.getLocalizedMessage();
            Toast.makeText(getApplicationContext(), msg,
                    Toast.LENGTH_LONG).show();
        }

        super.onDestroy();
    }

}
