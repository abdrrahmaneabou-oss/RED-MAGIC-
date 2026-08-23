package com.redmagic.tgktest;

interface ITgkService {
    int initBackend();
    int tapKey(int linuxKeyCode);
    String getStatus();
    int getBackendUid();
    void destroy();
}
