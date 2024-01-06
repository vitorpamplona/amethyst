/**
 * Copyright (c) 2023 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.utils.Robohash
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class RobohashBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    val warmHex = "f4f016c739b8ec0d6313540a8b12cf48a72b485d38338627ec9d427583551f9a"
    val testHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"
    val resultingSVG =
        """
        <svg
        	xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300">
        	<defs>
        		<style>.cls-bg{fill:#b2d7a6;}.cls-fill-1{fill:#4e5647;}.cls-fill-2{fill:#4e5647;}.cls-11-2{fill:#fff;}.cls-11-2,.cls-11-6{fill-opacity:0.2;}.cls-11-3{fill:none;}.cls-11-3,.cls-11-4{stroke:#000;stroke-miterlimit:10;}.cls-11-4{fill:#6d6e70;}.cls-11-5{opacity:0.2;}.cls-34-2,.cls-34-3{fill:#fff;}.cls-34-2{fill-opacity:0.4;}.cls-34-3,.cls-34-5{fill-opacity:0.2;}.cls-34-3,.cls-34-4{stroke:#000;stroke-miterlimit:10;}.cls-34-4{fill:none;}.cls-29-2{fill:#fff;}.cls-29-2,.cls-29-4{fill-opacity:0.4;}.cls-29-3{fill:none;}.cls-29-3,.cls-29-4{stroke:#000;stroke-linecap:round;stroke-linejoin:round;}.cls-29-4{stroke-width:0.75px;}.cls-46-2{fill-opacity:0.4;}.cls-46-3{fill:none;}.cls-46-3,.cls-46-4{stroke:#000;stroke-linecap:round;stroke-linejoin:round;}.cls-46-4{fill:#461917;stroke-width:0.5px;}.cls-07-10,.cls-07-2,.cls-07-4,.cls-07-8,.cls-07-9{fill:none;}.cls-07-10,.cls-07-2,.cls-07-3,.cls-07-4,.cls-07-5,.cls-07-6,.cls-07-8,.cls-07-9{stroke:#000;}.cls-07-2,.cls-07-3,.cls-07-4,.cls-07-5,.cls-07-6,.cls-07-8{stroke-linecap:round;}.cls-07-2,.cls-07-3,.cls-07-4,.cls-07-5,.cls-07-6,.cls-07-8,.cls-07-9{stroke-linejoin:round;}.cls-07-3,.cls-07-5{fill:#fff;}.cls-07-3,.cls-07-5,.cls-07-6,.cls-07-7{fill-opacity:0.2;}.cls-07-3,.cls-07-4{stroke-width:0.75px;}.cls-07-10,.cls-07-8,.cls-07-9{stroke-width:1.5px;}.cls-07-10{stroke-miterlimit:10;}</style>
        	</defs>
        	<rect width="300" height="300" class="cls-bg" />
        	<g>
        		<path class="cls-fill-1" d="M160.5,246.5s-14-3-27,13-22,45-22,45h108s-20-35-25-40S176.5,247.5,160.5,246.5Z"/>
        		<path class="cls-11-2" d="M121.5,303.5s5-24,10-33,3.28-8.07,7.64-8.53,15.13-1.22,23.75,2.66,8.18,3.25,8.18,3.25l13.12-12.15s-12.68-9.66-26.18-9.44-23.5,12.22-26.5,15.22-14.17,23.45-19.09,40.22Z"/>
        		<path class="cls-11-3" d="M145.5,261.5s28,1,38,17,13,26,13,26h-86s13.77-34.15,18.39-38.57S144.5,261.5,145.5,261.5Z"/>
        		<path class="cls-11-2" d="M121.5,303.5s5-24,10-33,3.28-8.07,7.64-8.53c22.36-1.47,31.93,5.9,31.93,5.9l13.12-12.15s-12.68-9.66-26.18-9.44-23.5,12.22-26.5,15.22-14.17,23.45-19.09,40.22Z"/>
        		<path class="cls-11-3" d="M160.5,246.5s-14-3-27,13-22,45-22,45h108s-20-35-25-40S176.5,247.5,160.5,246.5Z"/>
        		<path class="cls-11-4" d="M149,192s1,62,2,64a11.16,11.16,0,0,0,9.06,3.21c5.44-.71,6.44-2.71,6.94-5.21,0,0-2.5-48.5-2.5-62.5Z"/>
        		<path class="cls-11-5" d="M159.5,191.5s3,36,3,40,1,27,1,27l3-1-2-66Z"/>
        		<path class="cls-11-3" d="M164.5,203.5a8.76,8.76,0,0,1-6,2c-4,0-8,0-9-1"/>
        		<path class="cls-11-3" d="M165.5,214.5a12.68,12.68,0,0,1-6,2c-3,0-7,.25-10-1.37"/>
        		<path class="cls-11-3" d="M165.8,225.5a10.11,10.11,0,0,1-6.3,2c-4,0-7.65-.2-9.82-2.1"/>
        		<path class="cls-11-3" d="M166,234.5s-.5,3-5.5,3a64.39,64.39,0,0,1-10.3-1"/>
        		<path class="cls-11-3" d="M166.5,245.5s-1,3-6,3a21.51,21.51,0,0,1-10-2"/>
        		<path class="cls-11-6" d="M195.58,301.91H218S203,275.78,198.73,270.14,186.5,256.5,184.5,255.5l-13,12s8,4.69,10.48,8.84S192.66,293.32,195.58,301.91Z"/>
        		<path class="cls-11-4" d="M193.5,272.5a6.85,6.85,0,0,0-2,8c2,5,8,6,8,6s7-6,15-2,9,7,9,15,14,3,14,3l2-2s4-21-19-31C220.5,269.5,209.5,265.5,193.5,272.5Z"/>
        		<path class="cls-11-6" d="M196.5,284.5a25.68,25.68,0,0,1,18-5c11,1,16,9,18,17s-4,10-4,10l-5-7s.05-13.88-10-15.44-14,2.44-14,2.44S195.5,286.5,196.5,284.5Z"/>
        		<path class="cls-11-3" d="M206,268.82a2.89,2.89,0,0,0-1.5,2.68c0,2,.1,8.87,7,11.94"/>
        		<path class="cls-11-3" d="M229.44,274.89s-3.94-1.39-5.94,1.61-3.62,9.25-2.81,12.13"/>
        		<path class="cls-11-3" d="M223.56,300.34c-.06.16.94-2.84,5.94-2.84a21.66,21.66,0,0,1,10.09,2.37"/>
        		<path class="cls-11-4" d="M127.07,268.36S108.5,263.5,98.5,279.5c0,0-5,9,1,20s7,4,7,4l7-5.25s-6-7.75-2-13.75c0,0,2.75-4.25,8.88-3.12Z"/>
        		<path class="cls-11-3" d="M119.5,281.5s2-7,1-10a5.45,5.45,0,0,0-3.56-3.62"/>
        		<path class="cls-11-3" d="M111.5,284.5a14.54,14.54,0,0,0-8-5c-5-1-5.44.94-5.44.94"/>
        		<path class="cls-11-3" d="M111,293.56a10.89,10.89,0,0,0-7.48.94c-4,2-4,5-4,5"/>
        		<path class="cls-11-6" d="M123.53,274.89s-7-2.39-13,.61-9,7-9,12,5,12,7,14,4.83-3.86,4.83-3.86-3.83-5.14-2.83-11.14c0,0,1-6,10-5Z"/>
        		<circle cx="145" cy="266" r="1.5"/>
        		<circle cx="160.5" cy="268.5" r="1.5"/>
        		<circle cx="173.5" cy="274.5" r="1.5"/>
        		<circle cx="182.5" cy="285.5" r="1.5"/>
        		<circle cx="188.5" cy="296.5" r="1.5"/>
        		<circle cx="133.5" cy="266.5" r="1.5"/>
        	</g>
        	<g>
        		<path class="cls-fill-1" d="M91.5,107.5s4,64,4,77,1,38,1,38,4,11,22,10c36.5.5,60-11,66-16,0,0-3-106-3-116s4-20-36-22c0,0-35.59,1.45-44.8,8.73,0,0-10.2,2.27-9.2,7.27S91.5,107.5,91.5,107.5Z"/>
        		<path class="cls-34-2" d="M95.8,102s2.7,53.53,3.7,68.53,5,42,5,45v14.71s-7-3.71-8-7.71-3.65-88.21-3.83-95.61S91.5,107.5,91.5,107.5v-13S91.1,100.43,95.8,102Z"/>
        		<path class="cls-34-3" d="M145.5,78.5s-53,5-54,16,23,14,37,13,52-7,53-16S156.5,77.5,145.5,78.5Z"/>
        		<path class="cls-34-4" d="M91.5,107.5s4,64,4,77,1,38,1,38,3,9,22,10c32,2,60-11,66-16,0,0-3-106-3-116s4-20-36-22c-16,.67-31.14,3.13-44.8,8.73-4.7,1.42-7.78,3.83-9.2,7.27Z"/>
        		<path class="cls-34-5" d="M168.5,103.5s1,49,1,57,1,35,1,41-1,22.92-1,22.92l15-7.92s-2.94-96.67-3-100.83,0-25.17,0-25.17c.34,3.51-3.36,6.57-11,9.17l-2,.72Z"/>
        		<circle cx="148" cy="114" r="1.5"/>
        		<circle cx="130.5" cy="116.5" r="1.5"/>
        		<circle cx="114.5" cy="117.5" r="1.5"/>
        		<circle cx="116.5" cy="225.5" r="1.5"/>
        		<circle cx="101.5" cy="220.5" r="1.5"/>
        		<circle cx="132.5" cy="225.5" r="1.5"/>
        		<circle cx="150.5" cy="221.5" r="1.5"/>
        		<circle cx="166.5" cy="217.5" r="1.5"/>
        		<circle cx="179.5" cy="212.5" r="1.5"/>
        		<circle cx="99.5" cy="112.5" r="1.5"/>
        		<circle cx="164.5" cy="109.5" r="1.5"/>
        		<circle cx="177.5" cy="104.5" r="1.5"/>
        	</g>
        	<path class="cls-fill-1" d="M129.5,123.5s-30,3-31,15,8,13,15,14,20-2,26-3,25,1,27-13-17-14-17-14Z"/>
        	<path class="cls-29-2" d="M116.92,125.8s-15.42,2.7-16.42,12.7,15.5,13,24.25,10.5A97.37,97.37,0,0,1,150,145.44c6.5.06,16.78-3.69,16.64-11.31S157.5,122.5,149.5,122.5s-21.12,1.13-21.12,1.13Z"/>
        	<path class="cls-29-2" d="M130.5,123.5l-8,25.5c1.6-.06,5.38-.65,9-1.2l7-24.8A47.26,47.26,0,0,1,130.5,123.5Z"/>
        	<polygon class="cls-29-2" points="120.77 124.91 113 150 118 150 125.41 124.06 120.77 124.91"/>
        	<path class="cls-29-3" d="M129.5,123.5s-30,3-31,15,8,13,15,14,20-2,26-3,25,1,27-13-17-14-17-14Z"/>
        	<path class="cls-29-4" d="M106.61,129.46s-6.11,3-6.11,9,5,13,20,11,23-4,28-4,17.29-2.5,18.15-10.25c0,0,.3,10.92-14.42,13.09s-16.25,1.2-21,2.68-28.06,4.23-32.4-7.15C98.84,143.87,95.71,134.43,106.61,129.46Z"/>
        	<g>
        		<g>
        			<path class="cls-fill-1" d="M122,180s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,122,180Z"/>
        			<path class="cls-fill-1" d="M131,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,131,179Z"/>
        			<path class="cls-fill-1" d="M141,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,141,179Z"/>
        			<path class="cls-fill-1" d="M149,178s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,149,178Z"/>
        		</g>
        		<path class="cls-46-2" d="M122,180s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,122,180Z"/>
        		<path class="cls-46-3" d="M122,180s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,122,180Z"/>
        		<path class="cls-46-4" d="M124.5,180.5s1,11,2,14a15.77,15.77,0,0,1,1,4s2.34-.11,2.17.44a31,31,0,0,0-.82-8.63,59,59,0,0,1-1.35-10.07v-.75h-3Z"/>
        		<line class="cls-46-4" x1="125.85" y1="200.49" x2="127.5" y2="198.5"/>
        		<path class="cls-46-2" d="M131,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,131,179Z"/>
        		<path class="cls-46-3" d="M131,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,131,179Z"/>
        		<path class="cls-46-4" d="M133.5,179.5s1,11,2,14a15.77,15.77,0,0,1,1,4s2.34-.11,2.17.44a31,31,0,0,0-.82-8.63,59,59,0,0,1-1.35-10.07v-.75h-3Z"/>
        		<line class="cls-46-4" x1="134.85" y1="199.49" x2="136.5" y2="197.5"/>
        		<path class="cls-46-2" d="M141,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,141,179Z"/>
        		<path class="cls-46-3" d="M141,179s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,141,179Z"/>
        		<path class="cls-46-4" d="M143.5,179.5s1,11,2,14a15.77,15.77,0,0,1,1,4s2.34-.11,2.17.44a31,31,0,0,0-.82-8.63,59,59,0,0,1-1.35-10.07v-.75h-3Z"/>
        		<line class="cls-46-4" x1="144.85" y1="199.49" x2="146.5" y2="197.5"/>
        		<path class="cls-46-2" d="M149,178s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,149,178Z"/>
        		<path class="cls-46-3" d="M149,178s1,10,2,14,1.5,6.5,1.5,6.5,3,0,4-1-2-17-2-18v-2s-1.49,0-3,.11A12,12,0,0,0,149,178Z"/>
        		<path class="cls-46-4" d="M151.5,178.5s1,11,2,14a15.77,15.77,0,0,1,1,4s2.34-.11,2.17.44a31,31,0,0,0-.82-8.63,59,59,0,0,1-1.35-10.07v-.75h-3Z"/>
        		<line class="cls-46-4" x1="152.85" y1="198.49" x2="154.5" y2="196.5"/>
        	</g>
        	<g>
        		<g>
        			<path class="cls-fill-1" d="M128.5,86.5a3.49,3.49,0,0,0-1,2v3s3,3,9,3,12-4,12-4v-5s-1-1-6-1S131.5,84.5,128.5,86.5Z"/>
        			<path class="cls-fill-1" d="M173.5,21.5l-35,17s-.57,2.41.22,3.71l.78,1.29,37-18S178.5,20.5,173.5,21.5Z"/>
        			<path class="cls-fill-1" d="M96.5,58.5s-2,2-1,3,3,0,3,0l33.66-15.9a6.44,6.44,0,0,1-.66-3.1Z"/>
        			<path class="cls-fill-1" d="M134.5,47.5l-1,37a4.33,4.33,0,0,0,4,2,7.65,7.65,0,0,0,5-2l-2-40S135.5,44.5,134.5,47.5Z"/>
        			<path class="cls-fill-1" d="M136.5,38.5l-4,2a3.76,3.76,0,0,0-.81,1.55,5.34,5.34,0,0,0-.19,1.45c0,2,2,4,3,4,0,0-.17-.54,1.42-1.77a8.26,8.26,0,0,1,4.32-1.22h.26s-2-2-2-3v-3Z"/>
        		</g>
        		<path class="cls-07-2" d="M134.5,47.5l-1,37a4.33,4.33,0,0,0,4,2,7.65,7.65,0,0,0,5-2l-2-40S135.5,44.5,134.5,47.5Z"/>
        		<path class="cls-07-3" d="M128.5,87.5s0,2,6,2,11-1,13-3c.48-.55.5-1.75-2.25-1.87-1,0-3.15,0-3.15,0a6.77,6.77,0,0,1-4.1,1.8c-3.5.1-4.31-1.6-4.31-1.6S128.5,85.5,128.5,87.5Z"/>
        		<path class="cls-07-2" d="M128.5,86.5a3.49,3.49,0,0,0-1,2v3s3,3,9,3,12-4,12-4v-5s-.8-1-5.8-1a7.15,7.15,0,0,1-4.57,2c-1.63,0-4.17,0-4.63-1.72A10.71,10.71,0,0,0,128.5,86.5Z"/>
        		<path class="cls-07-4" d="M128.5,86.5a3.49,3.49,0,0,0-1,2v3s3,3,9,3,12-4,12-4v-5c-.63-.65-1.9-1.15-6-1a6.16,6.16,0,0,1-4.17,1.8c-3.83.2-4.41-1.46-4.41-1.46S129.5,85.5,128.5,86.5Z"/>
        		<path class="cls-07-2" d="M136.5,38.5l-4,2a3.76,3.76,0,0,0-.81,1.55,5.34,5.34,0,0,0-.19,1.45c0,2,2,4,3,4,0,0-.17-.54,1.42-1.77a8.26,8.26,0,0,1,4.32-1.22h.26s-2-2-2-3v-3Z"/>
        		<path class="cls-07-5" d="M96.5,58.5s-2,2-1,3,3,0,3,0l33.8-15.6a7.45,7.45,0,0,1-.8-3.4Z"/>
        		<path class="cls-07-5" d="M173.5,21.5l-35,17s-.57,2.41.22,3.71l.78,1.29,37-18C177.52,25.52,179.49,20.53,173.5,21.5Z"/>
        		<circle class="cls-07-6" cx="175.5" cy="23.5" r="2"/>
        		<path d="M137.5,5.5s-2,0,1,2,32,15,32,15l2-1-33-15Z"/>
        		<path d="M56.72,44a1.7,1.7,0,0,0,1.2,1.19L96.29,58.83l1.89-1.2L58.81,44.1S56.44,43,56.72,44Z"/>
        		<path d="M114.5,17.5s-2,0,1,2,32,15,32,15l2-1-33-15Z"/>
        		<path d="M102,26.53s-2,.12,1.12,1.94S133,40.2,133,40.2l1.91-.77-30.83-12Z"/>
        		<path d="M177.5,24.19,212,41c1,1,.39,1.37-2,1L176.5,25.5S177,25.38,177.5,24.19Z"/>
        		<path d="M154.91,35.41l35.6,14.34S192,51,188.58,50.88L154,36.79Z"/>
        		<polygon points="102.89 59.02 134.34 68.69 133.65 69.98 101 59.98 102.89 59.02"/>
        		<path d="M142,42.54l35.55,13c2.4.9.47,2.47-2,1.19L140.2,43.79Z"/>
        		<path class="cls-07-7" d="M136,38.75a5.37,5.37,0,0,0-.5,2.75c0,2,2.22,3.37,2.22,3.37l1.8,40.84L144,88.34v4l4.46-1.89v-5a35.36,35.36,0,0,0-6-1l-2-40a3.7,3.7,0,0,1-2-3v-3Z"/>
        		<path class="cls-07-8" d="M119.5,47.5l-32-12s-3-1-4,1,0,3,2,4,27,10,27,10"/>
        		<line class="cls-07-8" x1="116.05" y1="49.09" x2="83.95" y2="37.51"/>
        		<line class="cls-07-9" x1="125.5" y1="49.2" x2="134.36" y2="52.57"/>
        		<line class="cls-07-10" x1="117.9" y1="52.66" x2="134" y2="59"/>
        		<line class="cls-07-10" x1="121.42" y1="50.93" x2="134.27" y2="55.83"/>
        		<path class="cls-07-10" d="M141,54.57,158,62s4,2,3,4-5,1-6,1-13.65-5.5-13.65-5.5"/>
        		<line class="cls-07-9" x1="141.18" y1="58.12" x2="160.5" y2="66.5"/>
        	</g>
        </svg>
        """
            .trimIndent()

    @Test
    fun createSVG() {
        // warm up
        Robohash.assemble(warmHex, true)
        benchmarkRule.measureRepeated {
            val result = Robohash.assemble(testHex, true)
            assertEquals(resultingSVG, result)
        }
    }
}
