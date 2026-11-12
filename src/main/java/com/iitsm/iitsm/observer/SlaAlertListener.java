package com.iitsm.iitsm.observer;

import com.iitsm.iitsm.model.SlaAlert;

public interface SlaAlertListener {
    void onAlert(SlaAlert alert);
}