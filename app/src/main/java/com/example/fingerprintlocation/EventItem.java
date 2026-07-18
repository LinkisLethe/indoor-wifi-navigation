package com.example.fingerprintlocation;

public class EventItem {
    public final String title;
    public final String date;       // yyyy-MM-dd
    public final String timeRange;  // HH:mm-HH:mm
    public final String speaker;    // Can be empty
    public final String venue;      // Address/Room
    public final String url;

    public EventItem(String title, String date, String timeRange, String speaker, String venue, String url) {
        this.title = title;
        this.date = date;
        this.timeRange = timeRange;
        this.speaker = speaker;
        this.venue = venue;
        this.url = url;
    }
}

