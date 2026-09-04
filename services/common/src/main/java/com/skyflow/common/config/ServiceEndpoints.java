package com.skyflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URLs of the other SkyFlow services. In Kubernetes these are the in-cluster service DNS
 * names, so no discovery client is needed.
 */
@ConfigurationProperties(prefix = "skyflow.services")
public class ServiceEndpoints {

    private String flight = "http://localhost:8081";
    private String booking = "http://localhost:8082";
    private String payment = "http://localhost:8083";
    private String notification = "http://localhost:8084";
    private String ai = "http://localhost:8085";
    private String user = "http://localhost:8086";

    public String getFlight() {
        return flight;
    }

    public void setFlight(String flight) {
        this.flight = flight;
    }

    public String getBooking() {
        return booking;
    }

    public void setBooking(String booking) {
        this.booking = booking;
    }

    public String getPayment() {
        return payment;
    }

    public void setPayment(String payment) {
        this.payment = payment;
    }

    public String getNotification() {
        return notification;
    }

    public void setNotification(String notification) {
        this.notification = notification;
    }

    public String getAi() {
        return ai;
    }

    public void setAi(String ai) {
        this.ai = ai;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
