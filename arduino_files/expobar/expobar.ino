

/*********************************************************************
This is an example for our Monochrome OLEDs based on SSD1306 drivers

  Pick one up today in the adafruit shop!
  ------> http://www.adafruit.com/category/63_98

This example is for a 128x64 size display using I2C to communicate
3 pins are required to interface (2 I2C and one reset)

Adafruit invests time and resources providing this open source code", 
please support Adafruit and open-source hardware by purchasing 
products from Adafruit!

Written by Limor Fried/Ladyada  for Adafruit Industries.  
BSD license", check license.txt for more information
All text above", and the splash screen must be included in any redistribution
*********************************************************************/

#include <SPI.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "Adafruit_MAX31855.h"
#include <Adafruit_MCP4725.h>
#include <TimerOne.h>

#include "MAX31855.h"

//const int doPin = 7;
//const int csPin = 6;
//const int clPin = 5;

//MAX31855 tc(clPin, csPin, doPin);

int thermoDO = 3;
int thermoCS = 4;
int thermoCLK = 5;
Adafruit_MAX31855 thermocouple(thermoCLK, thermoCS, thermoDO);

Adafruit_MCP4725 dac;
// Connect MCP4725 SCL to Arduino Nano A5
// Connect MCP4725 SDA to Arduino Nano A4

#define OLED_RESET 4
Adafruit_SSD1306 display(OLED_RESET);

#define LOGO16_GLCD_HEIGHT 16 
#define LOGO16_GLCD_WIDTH  16 

#define LINE_PRESSURE_PREINFUSE_SECS 7
#define PRESSURE_MAX 10.0
#define PRESSURE_TARGET 8.7

#define MOTOR_MAX 1100
#define MOTOR_CHANGE_STG1 20
#define MOTOR_CHANGE_STG2 15
#define MOTOR_CHANGE_STG3 5
#define MOTOR_CHANGE_STG4 1
#define MOTOR_CHANGE_STG5 0

#define MOTOR_INIT 300

#define SERIAL_LOG_PRESSURE 1
#define SERIAL_LOG_TEMP 1
#define SERIAL_LOG_MOTOR 1

#define OLED_DISPLAY_EN 1

#if (SSD1306_LCDHEIGHT != 64)
#error("Height incorrect", please fix Adafruit_SSD1306.h!");
#endif

float shotTimerSeconds = 0.0;
boolean preInfuse = true;

//int dialPin = 0;
//int dialReading = 0;

int powerRelayPin = 10;

int pressurePin = 0;
float pressureReading = 0;
int motorPin = 11;
boolean motorInputOn = false;
int motorSetting = 0;
int motorChange = 0;

int solenoidPin = 12;
int solenoidReading = 1; //MID400 is active low, so 1 means it is off

float temperature;
float readTemp;

//storage variables
boolean toggle0 = 0;
boolean toggle1 = 0;
boolean toggle2 = 0;

boolean enable_count = false;

void setup()   {  
  
  Serial.begin(115200);
  
  if (OLED_DISPLAY_EN)
    display.begin(SSD1306_SWITCHCAPVCC, 0x3C);  // initialize with the I2C addr 0x3D (for the 128x64)
  // init done
  
  pinMode(13, OUTPUT);
  pinMode(powerRelayPin, OUTPUT);
  digitalWrite(powerRelayPin, 0);
  
  pinMode(motorPin, INPUT);
  pinMode(solenoidPin, INPUT);
  
  dac.begin(0x62); //set the I2C slave addr of the DAC so we can talk to it
  dac.setVoltage(0, false);
  
  Timer1.initialize(100000); // set a timer of length 100000 microseconds (or 0.1 sec - or 10Hz 
  Timer1.attachInterrupt( timerIsr ); // attach the service routine here
  
  delay(5000); //wait 5 seconds until we enable motor power supply
  digitalWrite(powerRelayPin, 1);
  
  motorChange = MOTOR_CHANGE_STG1;
  
  //tc.begin();
}

/// --------------------------
/// Custom ISR Timer Routine
/// --------------------------
void timerIsr()
{
  if (enable_count)
    shotTimerSeconds += 0.1;
}


void loop() {
  
  pressureReading = (analogRead(pressurePin)*0.02523)-2.59; 
  
  //digital reads on the MID400s
  if (digitalRead(motorPin) == 0)
    motorInputOn = true; //active low
  else
    motorInputOn = false;
  
  //currently not using the second MID400 monitor
  //solenoidReading = digitalRead(solenoidPin);
  
  if (OLED_DISPLAY_EN) {
    display.clearDisplay();
    display.setTextSize(2);
    display.setTextColor(WHITE); // 'inverted' text
    display.setCursor(0,0);
    display.print(pressureReading,1);  
    display.println(" bar"); 
    display.setTextSize(1);
    display.print("\n");
  }
  
  
    
  if (motorInputOn == true && enable_count == false)
  {
    //this case is for when motor first starts
     motorSetting = MOTOR_INIT;
     shotTimerSeconds = 0;
     enable_count = true;
  }
  else if (motorInputOn == true && enable_count == true) 
  {
     //motor running case
     if (shotTimerSeconds > LINE_PRESSURE_PREINFUSE_SECS) 
     {
        preInfuse = false;
        
        if (motorSetting < MOTOR_MAX && pressureReading < PRESSURE_TARGET)
        {
          if (motorChange == MOTOR_CHANGE_STG2)
            motorChange = MOTOR_CHANGE_STG3;
          else if (motorChange == MOTOR_CHANGE_STG5)
            motorChange = MOTOR_CHANGE_STG5;
            
          motorSetting += motorChange; //linear ramp up interval per delay unit
        }
        else if (pressureReading > PRESSURE_TARGET)
        {
          if (motorChange == MOTOR_CHANGE_STG1)
            motorChange = MOTOR_CHANGE_STG2;
          else if (motorChange == MOTOR_CHANGE_STG3)
            motorChange = MOTOR_CHANGE_STG4;
            
          motorSetting -= motorChange; //back off from the overshoot
        }
        
        dac.setVoltage(motorSetting, false);
     }
     else
        preInfuse = true;
     
  }
  else
  {
     motorChange = MOTOR_CHANGE_STG1;
     motorSetting = 0;
     dac.setVoltage(motorSetting, false);
     enable_count = false;
  }

  readTemp = thermocouple.readFarenheit();
  if (isnan(readTemp) == false)
    temperature = readTemp;
  
  //tc 2 step process
 // if (tc.read() == 0) //make sure valid status 
 //   temperature = tc.getTemperature();
  
    
  if (OLED_DISPLAY_EN) {
    display.setTextSize(2);
    if (preInfuse == true)
        display.print("P");
    display.print(motorSetting);
    display.print(" ");
    display.println(shotTimerSeconds,1);
    display.setTextSize(1);
    display.print("\n");
    display.setTextSize(2);
    display.print(temperature,1);
    display.print("F");
    display.display();
  }
  
  if (SERIAL_LOG_PRESSURE)
  {
    Serial.print("[P]");
    Serial.print(pressureReading,1);
    Serial.print(",");
  }
  if (SERIAL_LOG_MOTOR)
  {
    Serial.print("[M]");
    Serial.print(motorSetting);
    Serial.print(",");
  }
  if (SERIAL_LOG_TEMP)
  {
    Serial.print("[T]");
    Serial.print(temperature,1);
    Serial.print(",");
  }
  
  delay(100);
}



