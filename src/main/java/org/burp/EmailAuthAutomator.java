package org.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailAuthAutomator implements BurpExtension {
    private MontoyaApi api;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Java Email Automator");
        api.http().registerHttpHandler(new EmailHandler());
    }

    private class EmailHandler implements HttpHandler {
        @Override
        public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
            // 認証エンドポイントを特定
            if (requestToBeSent.url().contains("/verify")) {
                String code = fetchLatestCode();
                if (code != null) {
                    // パラメータ "code" を動的に書き換え
                    HttpRequest updatedRequest = requestToBeSent.withUpdatedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.parameter("code", code, burp.api.montoya.http.message.params.HttpParameterType.BODY)
                    );
                    return RequestToBeSentAction.continueWith(updatedRequest);
                }
            }
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }

        private String fetchLatestCode() {
            try {
                // 1. Mailpitのメッセージ一覧を取得
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8025/api/v1/messages?limit=1"))
                        .GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                // 簡易的に正規表現でIDを抽出（本来はJSONライブラリ推奨）
                Matcher idMatcher = Pattern.compile("\"ID\":\"([^\"]+)\"").matcher(response.body());

                if (idMatcher.find()) {
                    String msgId = idMatcher.group(1);
                    // 2. メールの詳細を取得
                    java.net.http.HttpRequest detailReq = java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8025/api/v1/message/" + msgId))
                            .GET().build();

                    HttpResponse<String> detailRes = httpClient.send(detailReq, HttpResponse.BodyHandlers.ofString());
                    // 3. 6桁の数字を抽出
                    Matcher codeMatcher = Pattern.compile("(\\d{6})").matcher(detailRes.body());
                    if (codeMatcher.find()) {
                        return codeMatcher.group(1);
                    }
                }
            } catch (Exception e) {
                api.logging().logToError("Mail fetch failed: " + e.getMessage());
            }
            return null;
        }

        @Override
        public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }
    }
}