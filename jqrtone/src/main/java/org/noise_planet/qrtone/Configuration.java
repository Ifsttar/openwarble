/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2018, Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.noise_planet.qrtone;

/**
 * QRTone configuration object
 */
public class Configuration {
  public enum ECC_LEVEL {ECC_L, ECC_M, ECC_Q, ECC_H}
  private static final int[][] ECC_SYMBOLS = new int[][] {{14, 2}, {14, 4}, {12, 6}, {10, 6}};
  public static final double MULT_SEMITONE = Math.pow(2, 1/15.0);
  public static final double DEFAULT_WORD_TIME = 0.06;
  public static final double DEFAULT_WORD_SILENCE_TIME = 0.01;
  public static final double DEFAULT_GATE_TIME = 0.12;
  public static final double DEFAULT_AUDIBLE_FIRST_FREQUENCY = 1720;
  public static final double DEFAULT_INAUDIBLE_FIRST_FREQUENCY = 18200;
  public static final int DEFAULT_INAUDIBLE_STEP = 50;
  public static final double DEFAULT_TRIGGER_SNR = 15;
  public static final ECC_LEVEL DEFAULT_ECC_LEVEL = ECC_LEVEL.ECC_Q;

  public final double sampleRate;
  public final double firstFrequency;
  public final int frequencyIncrement;
  public final double frequencyMulti;
  public final double wordTime;
  public final double triggerSnr;
  public final double gateTime;
  public final double wordSilenceTime;

  public Configuration(double sampleRate, double firstFrequency, int frequencyIncrement, double frequencyMulti,
                       double wordTime, double triggerSnr, double gateTime, double wordSilenceTime) {
    this.sampleRate = sampleRate;
    this.firstFrequency = firstFrequency;
    this.frequencyIncrement = frequencyIncrement;
    this.frequencyMulti = frequencyMulti;
    this.wordTime = wordTime;
    this.triggerSnr = triggerSnr;
    this.gateTime = gateTime;
    this.wordSilenceTime = wordSilenceTime;
  }

  /**
   * Audible data communication
   * @param sampleRate Sampling rate in Hz
   * @return Default configuration for this profile
   */
  public static Configuration getAudible(double sampleRate) {
    return new Configuration(sampleRate, DEFAULT_AUDIBLE_FIRST_FREQUENCY,0, MULT_SEMITONE,
            DEFAULT_WORD_TIME, DEFAULT_TRIGGER_SNR, DEFAULT_GATE_TIME, DEFAULT_WORD_SILENCE_TIME);
  }


  /**
   * Inaudible data communication (from 18200 Hz to 22040 hz)
   * @param sampleRate Sampling rate in Hz. Must be greater or equal to 44100 Hz (Nyquist frequency)
   * @return Default configuration for this profile
   */
  public static Configuration getInaudible(double sampleRate) {
    return new Configuration(sampleRate, DEFAULT_INAUDIBLE_FIRST_FREQUENCY, DEFAULT_INAUDIBLE_STEP, 0,
            DEFAULT_WORD_TIME, DEFAULT_TRIGGER_SNR, DEFAULT_GATE_TIME, DEFAULT_WORD_SILENCE_TIME);
  }

  public double[] computeFrequencies(int frequencyCount) {
    return computeFrequencies(frequencyCount, 0);
  }
  public double[] computeFrequencies(int frequencyCount, double offset) {
    double[] frequencies = new double[frequencyCount];
    // Precompute pitch frequencies
    for(int i = 0; i < frequencyCount; i++) {
      if(frequencyIncrement != 0) {
        frequencies[i] = firstFrequency + (i + offset) * frequencyIncrement;
      } else {
        frequencies[i] = firstFrequency * Math.pow(frequencyMulti, i + offset);
      }
    }
    return frequencies;
  }

  /**
   * Given sample rate and frequencies, evaluate the minimal good window size for avoiding leaks of spectral analysis.
   * @param sampleRate Sample rate in Hz
   * @param targetFrequency Spectral analysis minimal frequency
   * @param closestFrequency Spectral analysis closest frequency to differentiate
   * @return Minimum window length in samples
   */
  public static int computeMinimumWindowSize(double sampleRate, double targetFrequency, double closestFrequency) {
    // Max bin size in Hz
    double max_bin_size = Math.abs(closestFrequency - targetFrequency) / 2.0;
    // Minimum window size without leaks
    int window_size = (int)(Math.ceil(sampleRate / max_bin_size));
    return Math.max(window_size, (int)Math.ceil(sampleRate*(5*(1/targetFrequency))));
  }

  /**
   * @param eccLevel Ecc level
   * @return Number of symbols (Payload+Ecc) corresponding to this level
   */
  public static int getTotalSymbolsForEcc(ECC_LEVEL eccLevel) {
    return ECC_SYMBOLS[eccLevel.ordinal()][0];
  }

  /**
   * @param eccLevel Ecc level
   * @return Number of Ecc symbols corresponding to this level (Correctable errors is 50 % of this number)
   */
  public static int getEccSymbolsForEcc(ECC_LEVEL eccLevel) {
    return ECC_SYMBOLS[eccLevel.ordinal()][1];
  }
}
