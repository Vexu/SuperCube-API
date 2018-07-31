package fi.vexu.supercubeapi;

/**
 * Created 28.7.2018.
 *
 * Copyright 2018 Vexu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public interface SuperCubeListener {
    void onStatusReceived();
    void onBatteryReceived(int battery);
    void onMovesReceived(int moves);
    void onResetReceived(byte[] response);
    void onOtherReceived(byte[] response);
    void onCubeReady();
    void onConnectionStateUpdated();

    /*void onResetSolved(byte[] response);
    void onResetCustom(byte[] response);
    void onMystery1Received(byte[] response);
    void onMystery2Received(byte[] response);
    void onMystery3Received(byte[] response);
    void onMystery4Received(byte[] response);*/
}
