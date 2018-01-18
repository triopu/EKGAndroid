#include <LiquidCrystal.h>
#include <SoftwareSerial.h>
#include <SPI.h>

#include <stdio.h>
#include <stdlib.h>

uint16_t Array[15000];

SoftwareSerial Bluetooth(2,3); //RX, TX
LiquidCrystal lcd(10, 9, 8, 7, 6, 5);

#define AD8232      A0
#define LOPositive  0
#define LONegative  1
#define detButton   19

#define M           5
#define N           30
#define winSize     250
#define HP_CONSTANT ((float)1/(float)M)

//Resulusi RNG
#define RAND_RES    100000000

//Variabel waktu
unsigned long foundTimeMicros     = 0;
unsigned long old_foundTimeMicros = 0;
unsigned long currentMicros   = 0;
unsigned long previousMicros  = 0; 

// Interval untuk mengambil sampel dan algoritma berulang (mikrodetik)
const long PERIOD   = 4000;


// Buffer lingkaran untuk BPM rataan
float bpm = 0;

#define BPM_BUFFER_SIZE 5
unsigned long bpm_buff[BPM_BUFFER_SIZE] = {0};
int bpm_buff_WR_idx = 0;
int bpm_buff_RD_idx = 0;

int tmp = 0;

char outStr[5];

/* Porsi yang berkaitan dengan deteksi Pan-Tompkins QRS */

// Buffer lingkaran untuk sinyal input EKG
// Kita perlu menyimpan riwayat M + 1 sampel untuk HP penyaring
float ecg_buff[M+1] = {0};
int ecg_buff_WR_idx = 0;
int ecg_buff_RD_idx = 0;

// Buffer lingkaran untuk sinyal input EKG
// Kita perlu menyimpan riwayat N + 1 sampel untuk LP penyaring
float hp_buff[N+1] = {0};
int hp_buff_WR_idx = 0;
int hp_buff_RD_idx = 0;

// LP penyaring mengeluarkan satu titik untuk setiap titik masukan
// Ini akan langsung ke penyaringan adaptif untuk eval
float next_eval_pt = 0;

// Menjalankan jumlah untuk HP dan LP filter, nilai-nilai bergeser di FILO
float hp_sum = 0;
float lp_sum = 0;

// Variabel bekerja untuk thresholding adaptif
float treshold = 0;
boolean triggered = false;
int trig_time = 0;
float win_max = 0;
int win_idx = 0;


// Jumlah iterasi awal, digunakan menentukan kapan jendela bergerak telah diisi
int number_iter = 0;

void setup() {
  Serial.begin(19200);
  Bluetooth.begin(115200);
  lcd.begin(16, 2);
  pinMode(LOPositive, INPUT);
  pinMode(LONegative, INPUT);
  pinMode(AD8232, INPUT);
  pinMode(detButton, INPUT);
}

void loop() {
  BeatRate();
}

void BeatRate(){
  currentMicros = micros();
  
  
  // Beralih jika sudah waktunya untuk titik data baru (menurut PERIODE)
  if(currentMicros - previousMicros >= PERIOD){
    previousMicros = currentMicros;

    
    boolean QRS_detected = false;
    // Hanya membaca data dan melakukan deteksi jika lead hidup
    
 
    // Hanya membaca data jika EKG Chip telah mendeteksi bahwa lead menempel pada pasien
    // boolean leads_are_on = (digitalRead(LOPositive) == 0) && (digitalRead(LONegative) == 0);
    boolean leads_are_on = true;
    if(leads_are_on){

      // Membaca data titik EKG berikutnya
      int next_ecg_pt = analogRead(AD8232);
      
      QRS_detected = detect(next_ecg_pt);
      
      if(QRS_detected == true){

        foundTimeMicros = micros();
        int bpm = (60.0/(((foundTimeMicros - old_foundTimeMicros))/1000000.0));
        lcd.setCursor(0,1);
        lcd.print((foundTimeMicros - old_foundTimeMicros)/1000000.0);

        // Mengirim data EKG melalui Bluetooth
        sprintf(outStr,"%04d",next_ecg_pt);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
        
        // Mengirim data BPM melalui Bluetooth
        sprintf(outStr,"%05d",bpm);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
        
        old_foundTimeMicros = foundTimeMicros;
      }
      else{
        // Mengirim data EKG melalui Bluetooth
        sprintf(outStr,"%03d",next_ecg_pt);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
      }
    } 
  }
}

boolean detect(float new_ecg_pt){
  // Copy titik baru ke dalam lingkaran buffer, indeks pertambahan
  ecg_buff[ecg_buff_WR_idx ++] = new_ecg_pt;
  ecg_buff_WR_idx %= (M+1);

  /*Penyaringan High Pass*/
  if(number_iter < M){
    // Pertama mengisi buffer dengan poin yang cukup untuk HP penyaring
    hp_sum += ecg_buff[ecg_buff_RD_idx];
    hp_buff[hp_buff_WR_idx] = 0;
  }
  else{
    hp_sum += ecg_buff[ecg_buff_RD_idx];

    tmp = ecg_buff_RD_idx - M;
    if(tmp<0) tmp += M+1;

    hp_sum -= ecg_buff[tmp];

    float y1 = 0;
    float y2 = 0;

    tmp = (ecg_buff_RD_idx - ((M+1)/2));
    if(tmp<0) tmp+= M+1;
    y2 = ecg_buff[tmp];
    y1 = HP_CONSTANT * hp_sum;
    hp_buff[hp_buff_WR_idx] = y2 - y1;
  }
  
  // Selesai membaca EKG penyangga, posisi kenaikan
  ecg_buff_RD_idx++;
  ecg_buff_RD_idx %= (M+1);

  // Selesai menulis untuk penyangga HP, posisi kenaikan
  hp_buff_WR_idx++;
  hp_buff_WR_idx %= (N+1);

  /*Penyaringan Low Pass*/
  // Pergeseran dalam sampel baru dari high pass filter
  lp_sum += hp_buff[hp_buff_RD_idx]*hp_buff[hp_buff_RD_idx];

  if(number_iter<N){
    next_eval_pt = 0;
  }
  else{
    tmp = hp_buff_RD_idx - N;
    if(tmp<0) tmp += (N+1);

    lp_sum -= hp_buff[tmp] * hp_buff[tmp];

    next_eval_pt = lp_sum;
  }

  // Selesai membaca penyangga HP, posisi kenaikan
  hp_buff_RD_idx++;
  hp_buff_RD_idx %= (N+1);

  /* Deteksi denyut adaptasi thresholding */
  //Mengatur threshold awal
  if(number_iter<winSize){
    if(next_eval_pt > treshold){
      treshold = next_eval_pt;
    }
    
    // Hanya kenaikan jumlah iter jika kurang dari winSize
    // Jika lebih besar, maka counter tidak melayani tujuan lebih lanjut
    number_iter++;
  }
    

  // Memeriksa apakah deteksi bertahan jangka waktu telah berlalu
  if(triggered == true){
    trig_time++;
    if(trig_time >= 100){
      triggered = false;
      trig_time = 0;
    }
  }

  // Mencari apakah kita memiliki max baru
  if(next_eval_pt > win_max) win_max = next_eval_pt;

  // Mencari apadakah kita diatas adaptiv threshold
  if(next_eval_pt > treshold && !triggered){
    triggered = true;
    return true;
  }
  
  // Lagi kita akan menyelesaikan fungsi sebelum kembali FALSE,
  // Berpotensi mengubah ambang
          
  // Menyesuaikan batas adaptif menggunakan sinyal maksimum yang ditemukan...
  // ...di jendela sebelumnya

  if(win_idx++ >= winSize){
    // Bobot faktor untuk menentukan kontribusi...
    // ...nilai puncak arus ke penyesuaian ambang
    float gamma = 0.175;

    // Melupakan faktor tingkat dimana kita melupakan pengamatan lalu
    // Pilih nilai acak antara 0.01 dan 0.1 untuk ini 
    float alpha = 0.01 + (((float)random(0,RAND_RES)/(float)(RAND_RES))*((0.1-0.01)));

    // Hitung Treshold baru
    treshold = alpha * gamma * win_max + (1-alpha)*treshold;

    // Reset window index sekarang
    win_idx = 0;
    win_max = -10000000;
  }
  return false;
}
