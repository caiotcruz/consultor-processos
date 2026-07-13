package com.consultorprocessos.admin.service;

public interface AdminEmailService {

 
    void sendCourtRequestAlert(String courtName, String courtCode,
                               String processNumber, long totalRequests);

    void sendHealthScoreAlert(String courtCode, int score);
}