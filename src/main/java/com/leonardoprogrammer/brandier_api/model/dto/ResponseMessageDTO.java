package com.leonardoprogrammer.brandier_api.model.dto;

public class ResponseMessageDTO {

    private String message;

    public ResponseMessageDTO() {
    }

    public ResponseMessageDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
