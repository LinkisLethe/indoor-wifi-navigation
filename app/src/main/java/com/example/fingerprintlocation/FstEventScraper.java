package com.example.fingerprintlocation;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FstEventScraper {

    // The page you specified
    public static final String LIST_URL = "https://fst.bnbu.edu.cn/en/news_events/events.htm";

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{2}:\\d{2}-\\d{2}:\\d{2})\\b");
    private static final Pattern VENUE_PATTERN = Pattern.compile("(T\\d+-\\d+.*)$"); // From T?-??? to the end is considered the venue

    public static List<EventItem> fetchLatest10() throws Exception {
        Document doc = Jsoup.connect(LIST_URL)
                .userAgent("Mozilla/5.0")
                .timeout(12000)
                .get();

        Elements links = doc.select("a"); // Select all first
        List<EventItem> out = new ArrayList<>();

        Pattern dateP = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

        for (Element a : links) {
            if (out.size() >= 10) break;

            String raw = a.text().replace('\u00A0', ' ').trim();
            if (raw.isEmpty()) continue;

            // Key: only consider it an event if it contains a date
            if (!dateP.matcher(raw).find()) continue;

            String url = a.absUrl("href");
            // Some a tags might not have an href, skip them
            if (url == null || url.isEmpty()) continue;

            EventItem item = parseOne(raw, url);
            if (item != null) out.add(item);
        }
        return out;

    }

    private static EventItem parseOne(String raw, String url) {
        // Typical raw format:
        // "<TITLE> 2025-12-10 15:10-16:10 Prof. XXX (...) T2-202"
        Matcher md = DATE_PATTERN.matcher(raw);
        if (!md.find()) return null;
        String date = md.group(1);
        int dateStart = md.start();

        Matcher mt = TIME_PATTERN.matcher(raw);
        if (!mt.find(md.end())) return null;
        String timeRange = mt.group(1);
        int timeEnd = mt.end();

        String title = raw.substring(0, dateStart).trim();

        String afterTime = raw.substring(timeEnd).trim(); // speaker + venue
        String speaker = "";
        String venue = "";

        Matcher mv = VENUE_PATTERN.matcher(afterTime);
        if (mv.find()) {
            int venueStart = mv.start();
            speaker = afterTime.substring(0, venueStart).trim();
            venue = afterTime.substring(venueStart).trim();
        } else {
            // If T?-??? is not matched, treat the rest as the venue (a few events might be venue names)
            venue = afterTime.trim();
        }

        return new EventItem(title, date, timeRange, speaker, venue, url);
    }
}
