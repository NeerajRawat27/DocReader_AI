package com.docureader.dto;

public class DemoSessionResponse {
    private String token;
    private Long userId;
    private String username;
    private int remainingQueries;
    private int totalQueriesAllowed;
    private long expiresAtTimestamp;

    public DemoSessionResponse() {
    }

    public DemoSessionResponse(String token, Long userId, String username, int remainingQueries, int totalQueriesAllowed, long expiresAtTimestamp) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.remainingQueries = remainingQueries;
        this.totalQueriesAllowed = totalQueriesAllowed;
        this.expiresAtTimestamp = expiresAtTimestamp;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRemainingQueries() {
        return remainingQueries;
    }

    public void setRemainingQueries(int remainingQueries) {
        this.remainingQueries = remainingQueries;
    }

    public int getTotalQueriesAllowed() {
        return totalQueriesAllowed;
    }

    public void setTotalQueriesAllowed(int totalQueriesAllowed) {
        this.totalQueriesAllowed = totalQueriesAllowed;
    }

    public long getExpiresAtTimestamp() {
        return expiresAtTimestamp;
    }

    public void setExpiresAtTimestamp(long expiresAtTimestamp) {
        this.expiresAtTimestamp = expiresAtTimestamp;
    }
}
