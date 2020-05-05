/**------------------------------------------------------------------------------
 *
 *  Receive and display chat messages on the Microsoft MXChip IoT DevKit.
 *
 *  @file MXChipChat.ino
 *
 *  @brief You can use the QRTone Android demo application to send audio message to the MXChip.
 * 
 * BSD 3-Clause License 
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
 * All rights reserved.
 *
 *----------------------------------------------------------------------------*/

/*
 * MXChip and Arduino native headers.
 */
#include "Arduino.h"
#include "OledDisplay.h"
#include "AudioClassV2.h"

/*
 * QRTone header
 */
#include "qrtone.h"

/*
 * The audio sample rate used by the microphone. 
 * As tones go from 1700 Hz to 8000 Hz, a sample rate of 16 KHz is the minimum.
 */
#define SAMPLE_RATE 16000

#define SAMPLE_SIZE 16

// audio circular buffer size
#define MAX_AUDIO_WINDOW_SIZE 512

// QRTone instance
qrtone_t* qrtone = NULL;

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

// Audio buffer used by MXChip audio controler
static char raw_audio_buffer[AUDIO_CHUNK_SIZE];

// Audio circular buffer provided to QRTone.
static int64_t scaled_input_buffer_feed_cursor = 0;
static int64_t scaled_input_buffer_consume_cursor = 0;
static float scaled_input_buffer[MAX_AUDIO_WINDOW_SIZE];

/**
 * Display message on Oled Screen
 * Message use a special format specified by the demo android app:
 * [field type int8_t][field_length int8_t][field_content int8_t array]
 * There is currently 2 type of field username(0) and message(1)à
 */
void process_message(void) {
  int32_t user_name = 0;
  int user_name_length = 0;
  int32_t message = 0;
  int message_length = 0;
  int8_t* data = qrtone_get_payload(qrtone);
  int32_t data_length = qrtone_get_payload_length(qrtone);
  int c = 0;
  while(c < data_length) {
    int8_t field_type = data[c++];
    if(field_type == 0) {
      // username
      user_name_length = (int32_t)data[c++];
      user_name = c;
      c += user_name_length;
    } else if(field_type == 1) {
      message_length = (int32_t)data[c++];
      message = c;
      c += message_length;
    }
  }
  if(user_name > 0 && message > 0) {
    Screen.clean();
    // Print username
    char buf[256];
    memset(buf, 0, 256);
    memcpy(buf, data + (size_t)user_name, user_name_length);
    Screen.print(0, buf, false);
    // Print message
    memset(buf, 0, 256);
    memcpy(buf, data + (size_t)message, message_length);
    Screen.print(1, buf, true);
  }
}

/**
 * Called by AudioClass when the audio buffer is full
 */
void recordCallback(void)
{
    int32_t record_buffer_length = Audio.readFromRecordBuffer(raw_audio_buffer, AUDIO_CHUNK_SIZE);
    char* cur_reader = &raw_audio_buffer[0];
    char* end_reader = &raw_audio_buffer[record_buffer_length];
    const int offset = 4; // short samples size + skip right channel
    int sample_index = 0;
    while(cur_reader < end_reader) {
      int16_t sample = *((int16_t *)cur_reader);
      scaled_input_buffer[(scaled_input_buffer_feed_cursor+sample_index) % MAX_AUDIO_WINDOW_SIZE] = (float)sample / 32768.0f;
      sample_index += 1;
      cur_reader += offset;
    }
    scaled_input_buffer_feed_cursor += sample_index;
}

void setup(void)
{
  Serial.begin(115200);
  Screen.init();
  Audio.format(SAMPLE_RATE, SAMPLE_SIZE);
  // disable automatic level control
  // Audio.setPGAGain(0x3F);

  // Allocate struct
  qrtone = qrtone_new();

  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);

  // Init callback method
  // qrtone_set_level_callback(qrtone, NULL, debug_serial);

  delay(100);
  
  // Start to record audio data
  Audio.startRecord(recordCallback);
  
  printIdleMessage();
}

void loop(void)
{
  // Once the recording buffer is full, we process it.
  if (scaled_input_buffer_feed_cursor > scaled_input_buffer_consume_cursor)
  {
    int sample_to_read = scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor;
    int max_window_length = qrtone_get_maximum_length(qrtone);
    int sample_index = 0;
    while(sample_index < sample_to_read) {
      int32_t position_in_buffer = ((scaled_input_buffer_consume_cursor + sample_index) % MAX_AUDIO_WINDOW_SIZE);
      int32_t window_length = min(max_window_length, min(sample_to_read - sample_index, MAX_AUDIO_WINDOW_SIZE - position_in_buffer % MAX_AUDIO_WINDOW_SIZE));
      if(qrtone_push_samples(qrtone, scaled_input_buffer + position_in_buffer, window_length)) {
        // Got a message
        process_message();
      }
      sample_index += window_length;
      max_window_length = qrtone_get_maximum_length(qrtone);      
    }
    scaled_input_buffer_consume_cursor += sample_index;
  }
}

void printIdleMessage()
{
  Screen.clean();
  Screen.print(0, "Awaiting message");
}
