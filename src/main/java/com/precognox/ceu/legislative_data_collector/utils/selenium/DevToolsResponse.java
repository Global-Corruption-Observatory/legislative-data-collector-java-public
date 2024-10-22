package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.Data;
import org.openqa.selenium.devtools.v123.network.model.Response;
import org.openqa.selenium.devtools.v123.network.model.ResponseReceived;

@Data
public class DevToolsResponse {

    private String url;
    private Response response;
    private String body;

    private String MIME;

    private int status;

    public DevToolsResponse(ResponseReceived responseReceived, String responseBody) {
        this.url = responseReceived.getResponse().getUrl();
        this.response = responseReceived.getResponse();
        this.MIME = responseReceived.getResponse().getMimeType();
        this.status = responseReceived.getResponse().getStatus();
        this.body = responseBody;
    }

    @Override
    public String toString() {
        return "DevToolsResponse{" +
                "url='" + url + '\'' +
                ", MIME='" + MIME + '\'' +
                ", status=" + status +
                '}';
    }
}
