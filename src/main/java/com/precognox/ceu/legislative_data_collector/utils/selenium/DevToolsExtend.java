package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v123.network.Network;
import org.openqa.selenium.devtools.v123.network.model.ResponseReceived;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@Slf4j
public class DevToolsExtend {

    private final static Predicate<ResponseReceived> DEFAULT_RECORDING_PREDICATE = responseReceived -> responseReceived.getResponse().getMimeType().equals("application/json");
    public final static Predicate<ResponseReceived> DOCUMENT_RECORDING_PREDICATE = responseReceived -> responseReceived.getResponse().getMimeType().equals("text/html") && responseReceived.getType().toString().equals("Document");
    private final static  BiFunction<DevTools, ResponseReceived, DevToolsResponse> DEFAULT_RECORDING_FUNCTION = (devTools, responseReceived) -> new DevToolsResponse(responseReceived, getResponseBody(devTools, responseReceived));

    public final static  BiFunction<DevTools, ResponseReceived, DevToolsResponse> HEADER_ONLY_RECORDING_FUNCTION = (devTools, responseReceived) -> new DevToolsResponse(responseReceived, null);


    private final DevTools devTool;
    @Setter
    private Predicate<ResponseReceived> recordingCondition = DEFAULT_RECORDING_PREDICATE;
    @Setter
    private BiFunction<DevTools, ResponseReceived, DevToolsResponse> recordingDataFunction = DEFAULT_RECORDING_FUNCTION;
    @Setter
    @Getter
    private List<DevToolsResponse> recordedData = new ArrayList<>();

    public DevToolsExtend(DevTools devTool) {
        this.devTool = devTool;
        initListener();
    }

    public DevToolsExtend(WebDriverWrapper webDriverWrapper) {
        this(webDriverWrapper.getWebDriver());
    }

    public DevToolsExtend(WebDriver webDriver) {
        if (webDriver instanceof HasDevTools) {
            this.devTool = ((HasDevTools) webDriver).getDevTools();
            initListener();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void initListener() {
        if(devTool.getCdpSession() == null) {
            devTool.createSessionIfThereIsNotOne();
            devTool.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            devTool.addListener(Network.responseReceived(), responseReceived -> {
                if (recordingCondition.test(responseReceived)) {
                    recordedData.add(recordingDataFunction.apply(this.devTool, responseReceived));
                }
            });
        }
    }

    public void startRecording() {
        recordedData.clear();
    }

    private String getResponseBody(ResponseReceived responseReceived) {
        return getResponseBody(this.devTool, responseReceived);
    }

    public static String getResponseBody(DevTools devToolsParam, ResponseReceived responseReceived) {
        log.warn("Response request Id: {}", responseReceived.getRequestId());
        log.warn("Response status: {}",responseReceived.getResponse().getStatus());
        log.warn("Response url: {}",responseReceived.getResponse().getUrl());
        Network.GetResponseBodyResponse response = devToolsParam.send(Network.getResponseBody(responseReceived.getRequestId()));
        return response.getBody();
    }

    public void clearListeners() {
        devTool.clearListeners();
    }


}
